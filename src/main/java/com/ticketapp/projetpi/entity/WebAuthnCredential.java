package com.ticketapp.projetpi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "webauthn_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 512)
    private String credentialId; // Base64URL encoded

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKey; // Base64URL encoded

    @Column(nullable = false)
    private long signCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
