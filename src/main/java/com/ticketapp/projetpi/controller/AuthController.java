package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.dto.*;
import com.ticketapp.projetpi.exception.InvalidCredentialsException;
import com.ticketapp.projetpi.service.AuthService;
import com.ticketapp.projetpi.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
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
}