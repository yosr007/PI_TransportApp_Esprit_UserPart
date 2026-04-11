package com.ticketapp.projetpi.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ticketapp.projetpi.dto.*;
import com.ticketapp.projetpi.entity.Role;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.exception.EmailAlreadyExistsException;
import com.ticketapp.projetpi.exception.InvalidCredentialsException;
import com.ticketapp.projetpi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

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
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Account is blocked. Please contact admin.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername());
    }

    public AuthResponse googleLogin(GoogleLoginRequest request) {
        String clientId = "619356278570-78j5d2dh4sumdou8ebdd4d5ijpqv8kkh.apps.googleusercontent.com";
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                throw new InvalidCredentialsException();
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // Auto-register new Google user
                user = new User();
                user.setEmail(email);
                user.setUsername(name);
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Random password for social users
                user.setRole(Role.USER);
                user.setProvider("GOOGLE");
                user.setProfilePic(pictureUrl);
                user.setEnabled(true);
                user = userRepository.save(user);
            } else {
                // Check if user is enabled
                if (!user.isEnabled()) {
                    throw new RuntimeException("Account is blocked. Please contact admin.");
                }
                // Optional: update profile pic if missing
                if (user.getProfilePic() == null) {
                    user.setProfilePic(pictureUrl);
                    userRepository.save(user);
                }
            }

            String token = jwtService.generateToken(user);
            return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername());

        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }
    }
}