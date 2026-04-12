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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FileStorageService fileStorageService;
    private final JavaMailSender mailSender;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${spring.mail.from}")
    private String fromEmail;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       FileStorageService fileStorageService,
                       JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.fileStorageService = fileStorageService;
        this.mailSender = mailSender;
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

        // --- 2FA Logic ---
        boolean isMfaRequired = false;

        // Skip 2FA for ADMIN
        if (!user.getRole().equals(Role.ADMIN)) {
            // Rule 1: First login (lastLoginAt is null)
            if (user.getLastLoginAt() == null) {
                isMfaRequired = true;
            } 
            // Rule 2: Pass more than 15 mins logged out
            else if (user.getLastLogoutAt() != null) {
                long minutesSinceLogout = Duration.between(user.getLastLogoutAt(), LocalDateTime.now()).toMinutes();
                if (minutesSinceLogout >= 15) {
                    isMfaRequired = true;
                }
            }
        }

        if (isMfaRequired) {
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setMfaCode(otp);
            user.setMfaExpiresAt(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            sendOtpEmail(user.getEmail(), otp);

            return new AuthResponse(user.getEmail(), true);
        }

        // Standard Login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername());
    }

    private void sendOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Your Login Verification Code");
        message.setText("Your verification code is: " + otp + "\nThis code will expire in 10 minutes.");
        mailSender.send(message);
    }

    public AuthResponse verifyMfa(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getMfaCode() == null || !user.getMfaCode().equals(code)) {
            throw new InvalidCredentialsException();
        }

        if (user.getMfaExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        // Reset MFA and update login time
        user.setMfaCode(null);
        user.setMfaExpiresAt(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername());
    }

    public void logout(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            user.setLastLogoutAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    public void initiateForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with this email"));

        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setMfaCode(otp);
        user.setMfaExpiresAt(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Password Reset Verification Code");
        message.setText("Your verification code to reset your password is: " + otp + "\nThis code will expire in 10 minutes.");
        mailSender.send(message);
    }

    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getMfaCode() == null || !user.getMfaCode().equals(code)) {
            throw new InvalidCredentialsException();
        }

        if (user.getMfaExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code expired");
        }

        // Update password and clear MFA
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMfaCode(null);
        user.setMfaExpiresAt(null);
        userRepository.save(user);
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