package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.UserSession;
import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<UserSession>> getSessions(@AuthenticationPrincipal Jwt jwt) {
        User user = userRepository.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<UserSession> sessions = sessionService.getUserSessions(user);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User user = userRepository.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        sessionService.revokeSession(id, user);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/current/jti")
    public ResponseEntity<String> getCurrentSessionJti(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(jwt.getId());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSession>> getUserSessionsForAdmin(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<UserSession> sessions = sessionService.getUserSessions(user);
        return ResponseEntity.ok(sessions);
    }
}
