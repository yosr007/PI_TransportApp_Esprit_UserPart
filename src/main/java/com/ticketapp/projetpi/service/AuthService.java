package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.dto.*;
import com.ticketapp.projetpi.entity.Role;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.exception.EmailAlreadyExistsException;
import com.ticketapp.projetpi.exception.InvalidCredentialsException;
import com.ticketapp.projetpi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FileStorageService fileStorageService;

    @Value("${jwt.expiration}")
    private long expiration;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.fileStorageService = fileStorageService;
    }

    public AuthResponse register(RegisterRequest request, org.springframework.web.multipart.MultipartFile profilePicFile) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        
        // Handle file upload
        if (profilePicFile != null && !profilePicFile.isEmpty()) {
            String filePath = fileStorageService.storeFile(profilePicFile);
            user.setProfilePic(filePath);
        } else if (request.getProfilePic() != null) {
            // Support existing URL/base64 path if no file is uploaded
            user.setProfilePic(request.getProfilePic());
        }

        user.setRole(Role.USER);

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic());
    }
}