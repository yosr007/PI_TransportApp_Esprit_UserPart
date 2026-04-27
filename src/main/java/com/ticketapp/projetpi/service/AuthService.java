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
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FileStorageService fileStorageService;
    private final JavaMailSender mailSender;
    private final SessionService sessionService;
    private final AnomalyDetectionServiceClient anomalyDetectionService;
    private final com.ticketapp.projetpi.repository.SecurityAlertRepository alertRepository;
    private final jakarta.servlet.http.HttpServletRequest request;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${spring.mail.from}")
    private String fromEmail;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       FileStorageService fileStorageService,
                       JavaMailSender mailSender,
                       SessionService sessionService,
                       AnomalyDetectionServiceClient anomalyDetectionService,
                       com.ticketapp.projetpi.repository.SecurityAlertRepository alertRepository,
                       jakarta.servlet.http.HttpServletRequest request) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.fileStorageService = fileStorageService;
        this.mailSender = mailSender;
        this.sessionService = sessionService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.alertRepository = alertRepository;
        this.request = request;
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

        // --- Require 2FA (Email Verification) immediately after registration ---
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setMfaCode(otp);
        user.setMfaExpiresAt(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        sendOtpEmail(user.getEmail(), otp);

        log.info("Registration successful, requiring 2FA for user: {}", user.getEmail());

        return new AuthResponse(null, 0, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), true);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            
            // Trigger alert on brute force attempt (3+ failures)
            if (user.getFailedLoginAttempts() >= 3) {
                String ip = sessionService.getClientIp(this.request);
                String location = sessionService.fetchLocation(ip);
                saveSecurityAlert(user, "Brute Force Attempt", 
                    String.format("Multiple failed login attempts detected (%d) from %s", 
                        user.getFailedLoginAttempts(), location), -1.0);
            }
            
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

            // AI Anomaly Detection Rule
            String ip = sessionService.getClientIp(this.request);
            String userAgent = this.request.getHeader("User-Agent");
            String location = sessionService.fetchLocation(ip);
            String device = sessionService.parseDevice(userAgent);

            AnomalyDetectionServiceClient.AnalysisResponse response = anomalyDetectionService.analyze(user, device, location, user.getFailedLoginAttempts());
            
            if (response != null) {
                log.info("AI Analysis Response: isAnomaly={}, score={}", response.is_anomaly(), response.getConfidence());
                if (response.is_anomaly()) {
                    isMfaRequired = true;
                    saveSecurityAlert(user, "Behavioral Anomaly", 
                        String.format("Suspicious successful login from %s using %s. Score: %.4f", 
                            location, device, response.getConfidence()), 
                        response.getConfidence());
                }
            } else {
                log.warn("AI Analysis Service returned NULL or timed out for user: {}", user.getEmail());
            }
        }

        log.info("Final Login Decision: email={}, isMfaRequired={}", user.getEmail(), isMfaRequired);
        if (isMfaRequired) {
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setMfaCode(otp);
            user.setMfaExpiresAt(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            sendOtpEmail(user.getEmail(), otp);

            return new AuthResponse(null, 0, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), true);
        }

        // Standard Login
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0); // Reset failures
        userRepository.save(user);
        
        String jti = UUID.randomUUID().toString();
        sessionService.createSession(user, jti, this.request);
        
        String token = jwtService.generateToken(user, jti);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), false);
    }

    private void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your Login Verification Code");
            message.setText("Your verification code is: " + otp + "\nThis code will expire in 10 minutes.");
            mailSender.send(message);
            log.info("OTP Email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send OTP email to {}: {}. Login will continue but user won't receive code.", 
                email, e.getMessage());
            // We don't rethrow to avoid 500 error, allowing the user to see the MFA screen 
            // even if mail delivery failed (useful for local debugging).
        }
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

        String jti = UUID.randomUUID().toString();
        sessionService.createSession(user, jti, this.request);

        String token = jwtService.generateToken(user, jti);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), false);
    }

    public void logout(String email, String jti) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            user.setLastLogoutAt(LocalDateTime.now());
            userRepository.save(user);
        }
        if (jti != null) {
            sessionService.revokeByJti(jti);
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

    public AuthResponse googleLogin(GoogleLoginRequest googleRequest) {
        String clientId = "619356278570-78j5d2dh4sumdou8ebdd4d5ijpqv8kkh.apps.googleusercontent.com";
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(googleRequest.getIdToken());
            if (idToken == null) {
                throw new RuntimeException("Invalid ID Token");
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

            String jti = UUID.randomUUID().toString();
            sessionService.createSession(user, jti, this.request);

            // AI Anomaly Detection for Google Login
            String ip = sessionService.getClientIp(this.request);
            String userAgent = this.request.getHeader("User-Agent");
            String location = sessionService.fetchLocation(ip);
            String device = sessionService.parseDevice(userAgent);
            
            boolean isMfaRequired = false;
            AnomalyDetectionServiceClient.AnalysisResponse response = anomalyDetectionService.analyze(user, device, location, 0);
            
            if (response != null && response.is_anomaly()) {
                // For Google login, we record the alert for the admin dashboard
                // but we might not always force MFA since Google has its own 2FA
                saveSecurityAlert(user, "Social Login Anomaly", 
                    String.format("Suspicious Google login from %s using %s. Score: %.4f", 
                        location, device, response.getConfidence()), 
                    response.getConfidence());
                
                // If you want to force MFA even for Google users, set this to true:
                // isMfaRequired = true; 
            }

            String token = jwtService.generateToken(user, jti);
            return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), isMfaRequired);

        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }
    }

    private void saveSecurityAlert(User user, String type, String details, double score) {
        log.info("PREPARING to save security alert: type={}, user={}", type, user.getEmail());
        try {
            com.ticketapp.projetpi.entity.SecurityAlert alert = com.ticketapp.projetpi.entity.SecurityAlert.builder()
                    .user(user)
                    .anomalyType(type)
                    .details(details)
                    .riskScore(score)
                    .build();
            alertRepository.saveAndFlush(alert);
            log.info("SUCCESS: Security alert [{}] saved and flushed for user: {}", type, user.getEmail());
        } catch (Exception e) {
            log.error("CRITICAL ERROR saving security alert: {}", e.getMessage(), e);
        }
    }

    public AuthResponse generateAuthResponse(User user) {
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        String jti = UUID.randomUUID().toString();
        sessionService.createSession(user, jti, this.request);

        String token = jwtService.generateToken(user, jti);
        return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), false);
    }
}