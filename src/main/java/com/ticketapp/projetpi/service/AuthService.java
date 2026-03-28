package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.dto.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(RegisterRequest request) {

        User user = new User();
        user.setUsername(request.username);
        user.setEmail(request.email);
        user.setPassword(passwordEncoder.encode(request.password));
        user.setRole("USER");

        userRepository.save(user);

        return "User registered";
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // 🔐 temporaire
        String token = jwtService.generateToken(user.getUsername());

        return new LoginResponse(
                token,
                86400000,
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}