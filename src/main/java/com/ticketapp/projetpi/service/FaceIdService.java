package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class FaceIdService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final String AI_SERVICE_URL = "http://localhost:5000/face/verify";
    private final Path storageLocation = Paths.get("uploads/faces");

    public FaceIdService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
        // Set longer timeout for AI processing
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setReadTimeout(10000); // 10 seconds
        factory.setConnectTimeout(2000);
        this.restTemplate.setRequestFactory(factory);
        
        try {
            Files.createDirectories(storageLocation);
        } catch (IOException e) {
            log.error("Could not initialize face storage", e);
        }
    }

    @Data
    @Builder
    public static class FaceVerifyRequest {
        private String source_image;
        private String target_image;
    }

    @Data
    public static class FaceVerifyResponse {
        private boolean verified;
        private double distance;
        private double similarity_percent;
        private String error;
    }

    public void registerFace(User user, String base64Image) throws IOException {
        String filename = user.getId().toString() + "_ref.jpg";
        Path targetPath = storageLocation.resolve(filename);
        
        String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
        byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
        Files.write(targetPath, imageBytes);
        
        user.setFaceIdPath(targetPath.toString());
        userRepository.save(user);
        log.info("Face registered for user: {}", user.getEmail());
    }

    public FaceVerifyResponse verifyFace(User user, String currentBase64) {
        if (user.getFaceIdPath() == null) {
            FaceVerifyResponse error = new FaceVerifyResponse();
            error.setError("No face data registered for this user");
            return error;
        }

        try {
            byte[] refBytes = Files.readAllBytes(Paths.get(user.getFaceIdPath()));
            String refBase64 = Base64.getEncoder().encodeToString(refBytes);

            FaceVerifyRequest request = FaceVerifyRequest.builder()
                    .source_image(refBase64)
                    .target_image(currentBase64)
                    .build();

            return restTemplate.postForObject(AI_SERVICE_URL, request, FaceVerifyResponse.class);
        } catch (Exception e) {
            log.error("Face verification failed: {}", e.getMessage());
            FaceVerifyResponse error = new FaceVerifyResponse();
            error.setError("AI Service error: " + e.getMessage());
            return error;
        }
    }
}
