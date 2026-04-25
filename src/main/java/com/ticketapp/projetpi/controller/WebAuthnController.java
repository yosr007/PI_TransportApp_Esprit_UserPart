package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.dto.AuthResponse;
import com.ticketapp.projetpi.dto.WebAuthnDTOs.*;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.service.WebAuthnService;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.yubico.webauthn.exception.AssertionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth/webauthn")
@Slf4j
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final UserRepository userRepository;

    public WebAuthnController(WebAuthnService webAuthnService, UserRepository userRepository) {
        this.webAuthnService = webAuthnService;
        this.userRepository = userRepository;
    }

    @GetMapping("/register/options")
    public ResponseEntity<RegistrationOptionsResponse> getRegistrationOptions(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.error("Unauthorized access attempt to registration options - jwt is null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId).orElseThrow();
            return ResponseEntity.ok(webAuthnService.getRegistrationOptions(user));
        } catch (Exception e) {
            log.error("Error generating registration options", e);
            throw e;
        }
    }

    @PostMapping("/register/finish")
    public ResponseEntity<Void> finishRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody String jsonResponse) throws RegistrationFailedException, IOException {
        if (jwt == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = userRepository.findById(userId).orElseThrow();
        webAuthnService.finishRegistration(user, jsonResponse);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/login/options")
    public ResponseEntity<AuthenticationOptionsResponse> getAuthenticationOptions() {
        return ResponseEntity.ok(webAuthnService.getAuthenticationOptions());
    }

    @PostMapping("/login/finish")
    public ResponseEntity<AuthResponse> finishAuthentication(@RequestBody String jsonResponse) throws Exception {
        return ResponseEntity.ok(webAuthnService.finishAuthentication(jsonResponse));
    }
}
