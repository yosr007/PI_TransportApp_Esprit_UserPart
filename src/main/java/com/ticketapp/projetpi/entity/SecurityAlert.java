package com.ticketapp.projetpi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "security_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String anomalyType;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    private double riskScore;
    
    private boolean isRead = false;

    @CreationTimestamp
    private LocalDateTime timestamp;
}
