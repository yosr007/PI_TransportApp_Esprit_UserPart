package com.ticketapp.projetpi.config;

import com.ticketapp.projetpi.repository.UserRepository;
import com.ticketapp.projetpi.repository.WebAuthnCredentialRepository;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.WebAuthnCredential;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebAuthnConfig {

    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    public WebAuthnConfig(WebAuthnCredentialRepository credentialRepository, UserRepository userRepository) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
    }

    @Bean
    public CredentialRepository yubicoCredentialRepository() {
        return new CredentialRepository() {
            @Override
            public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
                User user = userRepository.findByEmail(username).orElseThrow();
                return credentialRepository.findAllByUser(user).stream()
                        .map(c -> {
                            try {
                                return PublicKeyCredentialDescriptor.builder()
                                        .id(ByteArray.fromBase64Url(c.getCredentialId()))
                                        .build();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toSet());
            }

            @Override
            public Optional<ByteArray> getUserHandleForUsername(String username) {
                return userRepository.findByEmail(username)
                        .map(user -> {
                            ByteBuffer bb = ByteBuffer.allocate(16);
                            bb.putLong(user.getId().getMostSignificantBits());
                            bb.putLong(user.getId().getLeastSignificantBits());
                            return new ByteArray(bb.array());
                        });
            }

            @Override
            public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
                return Optional.empty();
            }

            @Override
            public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
                return credentialRepository.findByCredentialId(credentialId.getBase64Url())
                        .map(c -> {
                            try {
                                return RegisteredCredential.builder()
                                        .credentialId(ByteArray.fromBase64Url(c.getCredentialId()))
                                        .userHandle(new ByteArray(ByteBuffer.allocate(16).putLong(c.getUser().getId().getMostSignificantBits()).putLong(c.getUser().getId().getLeastSignificantBits()).array()))
                                        .publicKeyCose(ByteArray.fromBase64Url(c.getPublicKey()))
                                        .signatureCount(c.getSignCount())
                                        .build();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }

            @Override
            public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
                return credentialRepository.findByCredentialId(credentialId.getBase64Url()).stream()
                        .map(c -> {
                            try {
                                return RegisteredCredential.builder()
                                        .credentialId(ByteArray.fromBase64Url(c.getCredentialId()))
                                        .userHandle(ByteArray.fromBase64Url(c.getUser().getId().toString().replace("-", "")))
                                        .publicKeyCose(ByteArray.fromBase64Url(c.getPublicKey()))
                                        .signatureCount(c.getSignCount())
                                        .build();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toSet());
            }
        };
    }

    @Bean
    public RelyingParty relyingParty(CredentialRepository repository) {
        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id("localhost") // Change to your domain in production
                .name("TicketApp PI")
                .build();

        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(repository)
                .origins(java.util.Collections.singleton("http://localhost:4200"))
                .build();
    }
}
