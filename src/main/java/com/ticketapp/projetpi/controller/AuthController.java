package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.service.JwtService;
import org.springframework.web.bind.annotation.*;
import com.ticketapp.projetpi.service.AuthService;
import com.ticketapp.projetpi.dto.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin("*")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
    @GetMapping("/validate")
    public String validate(@RequestHeader("Authorization") String header) {

        String token = header.replace("Bearer ", "");

        if (jwtService.isTokenValid(token)) {
            return jwtService.extractUsername(token);
        }

        throw new RuntimeException("Invalid token");
    }
}
