package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.UserSession;
import com.ticketapp.projetpi.repository.UserSessionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnomalyDetectionServiceClient {

    private final UserSessionRepository sessionRepository;
    private final RestTemplate restTemplate;
    private final String AI_SERVICE_URL = "http://localhost:5000/analyze";

    public AnomalyDetectionServiceClient(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.restTemplate = new RestTemplate();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate.setRequestFactory(factory);
    }

    @Data
    @Builder
    public static class LoginAttemptDto {
        private int hour;
        private int day_of_week;
        private String device;
        private String location;
        private int failed_attempts;
    }

    @Data
    @Builder
    public static class AnalysisRequest {
        private String user_id;
        private String email;
        private LoginAttemptDto current;
        private List<LoginAttemptDto> history;
    }

    @Data
    public static class AnalysisResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("is_anomaly")
        private boolean is_anomaly;
        
        @com.fasterxml.jackson.annotation.JsonProperty("confidence")
        private double confidence;
        
        @com.fasterxml.jackson.annotation.JsonProperty("prediction")
        private int prediction;
    }

    public AnalysisResponse analyze(User user, String device, String location, int failedAttempts) {
        try {
            List<UserSession> sessions = sessionRepository.findByUserOrderByLoginAtDesc(user);

            LoginAttemptDto currentAttempt = LoginAttemptDto.builder()
                    .hour(LocalDateTime.now().getHour())
                    .day_of_week(LocalDateTime.now().getDayOfWeek().getValue())
                    .device(device != null ? device : "Unknown")
                    .location(location != null ? location : "Unknown")
                    .failed_attempts(failedAttempts)
                    .build();

            List<LoginAttemptDto> history = sessions.stream().limit(20).map(s -> LoginAttemptDto.builder()
                    .hour(s.getLoginAt() != null ? s.getLoginAt().getHour() : 0)
                    .day_of_week(s.getLoginAt() != null ? s.getLoginAt().getDayOfWeek().getValue() : 0)
                    .device(s.getDevice() != null ? s.getDevice() : "Unknown")
                    .location(s.getLocation() != null ? s.getLocation() : "Unknown")
                    .failed_attempts(0) 
                    .build()).collect(Collectors.toList());

            AnalysisRequest request = AnalysisRequest.builder()
                    .user_id(user.getId().toString())
                    .email(user.getEmail())
                    .current(currentAttempt)
                    .history(history)
                    .build();

            AnalysisResponse response = restTemplate.postForObject(AI_SERVICE_URL, request, AnalysisResponse.class);

            if (response != null) {
                log.info("Anomaly detection for user {}: isAnomaly={}, confidence={}",
                        user.getEmail(), response.is_anomaly(), response.getConfidence());
                return response;
            }
        } catch (Exception e) {
            log.error("Failed to call anomaly detection service: {}", e.getMessage());
        }
        return null;
    }
}
