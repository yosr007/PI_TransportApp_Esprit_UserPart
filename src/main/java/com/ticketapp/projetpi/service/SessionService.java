package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.UserSession;
import com.ticketapp.projetpi.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public void createSession(User user, String jti, HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        String location = fetchLocation(ip);
        String browser = parseBrowser(userAgent);
        String device = parseDevice(userAgent);

        UserSession session = UserSession.builder()
                .user(user)
                .tokenJti(jti)
                .ipAddress(ip)
                .location(location)
                .browser(browser)
                .device(device)
                .active(true)
                .build();

        sessionRepository.save(session);
    }

    public List<UserSession> getUserSessions(User user) {
        return sessionRepository.findByUserOrderByLoginAtDesc(user);
    }

    public void revokeSession(UUID sessionId, User user) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        if (!session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        session.setActive(false);
        sessionRepository.save(session);
    }

    public boolean isSessionActive(String jti) {
        return sessionRepository.findByTokenJti(jti)
                .map(UserSession::isActive)
                .orElse(false);
    }

    public void revokeByJti(String jti) {
        sessionRepository.findByTokenJti(jti).ifPresent(session -> {
            session.setActive(false);
            sessionRepository.save(session);
        });
    }

    public String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String fetchLocation(String ip) {
        try {
            if (ip.equals("127.0.0.1") || ip.startsWith("0:0")) return "Localhost";
            
            // Check for private IP ranges
            if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                (ip.startsWith("172.") && isPrivateLargeRange(ip))) {
                return "Local Network";
            }

            Map<String, Object> response = restTemplate.getForObject("http://ip-api.com/json/" + ip, Map.class);
            if (response != null && "success".equals(response.get("status"))) {
                return response.get("city") + ", " + response.get("country");
            }
        } catch (Exception e) {
            return "Unknown Location";
        }
        return "Unknown Location";
    }

    private boolean isPrivateLargeRange(String ip) {
        try {
            int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    private String parseBrowser(String ua) {
        if (ua == null) return "Unknown";
        ua = ua.toLowerCase();
        if (ua.contains("edg")) return "Edge";
        if (ua.contains("chrome")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari")) return "Safari";
        if (ua.contains("opera") || ua.contains("opr")) return "Opera";
        return "Browser";
    }

    public String parseDevice(String ua) {
        if (ua == null) return "Unknown";
        ua = ua.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "Mobile";
        if (ua.contains("tablet") || ua.contains("ipad")) return "Tablet";
        return "PC";
    }
}
