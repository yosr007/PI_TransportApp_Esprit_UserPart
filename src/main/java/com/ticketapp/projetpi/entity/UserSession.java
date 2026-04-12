package com.ticketapp.projetpi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String ipAddress;
    private String browser;
    private String device;
    private String location;
    
    @Column(unique = true, nullable = false)
    private String tokenJti; // JWT Unique ID

    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime loginAt;
    
    private LocalDateTime lastSeenAt;
}
