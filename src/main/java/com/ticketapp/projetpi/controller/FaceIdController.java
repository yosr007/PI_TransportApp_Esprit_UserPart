package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.dto.AuthResponse;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.service.AuthService;
import com.ticketapp.projetpi.service.FaceIdService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/face")
@Slf4j
@RequiredArgsConstructor
public class FaceIdController {

    private final FaceIdService faceIdService;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Data
    public static class FaceIdRequest {
        private String email;
        private String image; // Base64
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerFace(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> request) {
        
        if (jwt == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId).orElseThrow();
            
            faceIdService.registerFace(user, request.get("image"));
            return ResponseEntity.ok(Map.of("message", "Face registered successfully"));
        } catch (Exception e) {
            log.error("Face registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginWithFace(@RequestBody FaceIdRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getFaceIdPath() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "AI Face ID is not set up for this account."));
            }

            FaceIdService.FaceVerifyResponse response = faceIdService.verifyFace(user, request.getImage());

            if (response.isVerified()) {
                log.info("Face ID Login Successful for user: {} (Similarity: {}%)", 
                        user.getEmail(), response.getSimilarity_percent());
                AuthResponse authResponse = authService.generateAuthResponse(user);
                return ResponseEntity.ok(authResponse);
            } else {
                log.warn("Face ID Login Failed for user: {} (Similarity: {}%)", 
                        user.getEmail(), response.getSimilarity_percent());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Face verification failed. Please try again."));
            }
        } catch (Exception e) {
            log.error("AI Face login internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "AI Service unavailable or internal error"));
        }
    }
}
