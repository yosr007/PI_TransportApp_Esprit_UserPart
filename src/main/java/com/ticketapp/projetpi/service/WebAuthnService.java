package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.dto.AuthResponse;
import com.ticketapp.projetpi.dto.WebAuthnDTOs.*;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.WebAuthnCredential;
import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.yubico.webauthn.exception.AssertionFailedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final HttpServletRequest request;

    // Simple in-memory storage for challenges (for production use Redis or similar)
    private final Map<ByteArray, PublicKeyCredentialCreationOptions> pendingRegistrations = new ConcurrentHashMap<>();
    private final Map<ByteArray, AssertionRequest> pendingAssertions = new ConcurrentHashMap<>();

    @Value("${jwt.expiration}")
    private long expiration;

    public WebAuthnService(RelyingParty relyingParty, 
                           WebAuthnCredentialRepository credentialRepository, 
                           UserRepository userRepository,
                           JwtService jwtService,
                           SessionService sessionService,
                           HttpServletRequest request) {
        this.relyingParty = relyingParty;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.request = request;
    }

    public RegistrationOptionsResponse getRegistrationOptions(User user) {
        log.info("Generating registration options for user: {}", user.getEmail());
        
        ByteArray userHandle;
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(user.getId().getMostSignificantBits());
        bb.putLong(user.getId().getLeastSignificantBits());
        userHandle = new ByteArray(bb.array());

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getUsername())
                .id(userHandle)
                .build();

        AuthenticatorSelectionCriteria selection = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        StartRegistrationOptions options = StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(selection)
                .build();

        try {
            PublicKeyCredentialCreationOptions creationOptions = relyingParty.startRegistration(options);
            
            // Store the options for the finish phase
            pendingRegistrations.put(creationOptions.getChallenge(), creationOptions);

            return RegistrationOptionsResponse.builder()
                    .userHandle(creationOptions.getUser().getId().getBase64Url())
                    .challenge(creationOptions.getChallenge().getBase64Url())
                    .rpName(creationOptions.getRp().getName())
                    .rpId(creationOptions.getRp().getId())
                    .userName(creationOptions.getUser().getName())
                    .userDisplayName(creationOptions.getUser().getDisplayName())
                    .excludeCredentials(creationOptions.getExcludeCredentials().map(list -> 
                        list.stream().map(c -> c.getId().getBase64Url()).collect(Collectors.toList())
                    ).orElse(Collections.emptyList()))
                    .build();
        } catch (Exception e) {
            log.error("CRITICAL ERROR in getRegistrationOptions for user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate registration options: " + e.getMessage(), e);
        }
    }

    public void finishRegistration(User user, String jsonResponse) throws RegistrationFailedException, IOException {
        log.info("Verifying registration for user: {}", user.getEmail());

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = 
                PublicKeyCredential.parseRegistrationResponseJson(jsonResponse);

        ByteArray challenge = pkc.getResponse().getClientData().getChallenge();
        PublicKeyCredentialCreationOptions originalRequest = pendingRegistrations.remove(challenge);

        if (originalRequest == null) {
            throw new RuntimeException("Registration challenge not found or expired");
        }

        FinishRegistrationOptions options = FinishRegistrationOptions.builder()
                .request(originalRequest)
                .response(pkc)
                .build();

        try {
            RegistrationResult result = relyingParty.finishRegistration(options);

            WebAuthnCredential credential = WebAuthnCredential.builder()
                    .user(user)
                    .credentialId(result.getKeyId().getId().getBase64Url())
                    .publicKey(result.getPublicKeyCose().getBase64Url())
                    .signCount(result.getSignatureCount())
                    .build();

            credentialRepository.save(credential);
            log.info("Successfully registered credential for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("FAILURE in finishRegistration for user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Registration verification failed: " + e.getMessage(), e);
        }
    }

    public AuthenticationOptionsResponse getAuthenticationOptions() {
        log.info("Generating authentication options");
        
        AssertionRequest assertionRequest = relyingParty.startAssertion(StartAssertionOptions.builder().build());
        
        // Store the request for the finish phase
        pendingAssertions.put(assertionRequest.getPublicKeyCredentialRequestOptions().getChallenge(), assertionRequest);

        return AuthenticationOptionsResponse.builder()
                .challenge(assertionRequest.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url())
                .rpId(assertionRequest.getPublicKeyCredentialRequestOptions().getRpId())
                .allowCredentials(assertionRequest.getPublicKeyCredentialRequestOptions().getAllowCredentials().map(list -> 
                    list.stream().map(c -> c.getId().getBase64Url()).collect(Collectors.toList())
                ).orElse(Collections.emptyList()))
                .build();
    }

    public AuthResponse finishAuthentication(String jsonResponse) throws Exception {
        log.info("Verifying authentication response");

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc = 
                PublicKeyCredential.parseAssertionResponseJson(jsonResponse);

        ByteArray challenge = pkc.getResponse().getClientData().getChallenge();
        AssertionRequest originalRequest = pendingAssertions.remove(challenge);

        if (originalRequest == null) {
            throw new AssertionFailedException("Authentication challenge not found or expired");
        }

        try {
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(originalRequest)
                    .response(pkc)
                    .build());

            if (result.isSuccess()) {
                String userEmail = result.getUsername();
                User user = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

                // Update sign count
                WebAuthnCredential credential = credentialRepository.findByCredentialId(pkc.getId().getBase64Url())
                        .orElseThrow();
                credential.setSignCount(result.getSignatureCount());
                credentialRepository.save(credential);

                // Issue JWT
                user.setLastLoginAt(java.time.LocalDateTime.now());
                userRepository.save(user);

                String jti = UUID.randomUUID().toString();
                sessionService.createSession(user, jti, this.request);
                String token = jwtService.generateToken(user, jti);

                return new AuthResponse(token, expiration, user.getId(), user.getEmail(), user.getRole().name(), user.getProfilePic(), user.getUsername(), false);
            }
            throw new RuntimeException("Authentication failed: assertion result unsuccessful");
        } catch (Exception e) {
            log.error("FAILURE in finishAuthentication: {}", e.getMessage(), e);
            throw new RuntimeException("Authentication verification failed: " + e.getMessage(), e);
        }
    }
}
