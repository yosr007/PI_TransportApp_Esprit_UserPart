// AuthResponse.java  (renamed from LoginResponse)
package com.ticketapp.projetpi.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private long expiresIn;
    private UUID userId;
    private String email;
    private String role;

    public AuthResponse(String token, long expiresIn, UUID userId, String email, String role) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
        this.role = role;
    }
}