package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.dto.*;
import com.ticketapp.projetpi.exception.InvalidCredentialsException;
import com.ticketapp.projetpi.service.AuthService;
import com.ticketapp.projetpi.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            @RequestPart("user") @Valid RegisterRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, file));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader("Authorization") String header) {
        String token = header.replace("Bearer ", "");

        if (!jwtService.isTokenValid(token)) {
            throw new InvalidCredentialsException();
        }

        UUID userId = jwtService.extractUserId(token);
        String email = jwtService.extractEmail(token);
        String role  = jwtService.extractRole(token);

        return ResponseEntity.ok(Map.of(
                "valid",  true,
                "userId", userId,
                "email",  email,
                "role",   role
        ));
    }

    @PostMapping("/verify-mfa")
    public ResponseEntity<AuthResponse> verifyMfa(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        return ResponseEntity.ok(authService.verifyMfa(email, code));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        String token = header.replace("Bearer ", "");
        String email = jwtService.extractEmail(token);
        authService.logout(email);
        return ResponseEntity.noContent().build();
    }
}