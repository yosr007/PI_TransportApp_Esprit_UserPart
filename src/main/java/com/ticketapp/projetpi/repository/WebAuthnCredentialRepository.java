package com.ticketapp.projetpi.repository;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    List<WebAuthnCredential> findAllByUser(User user);
}
