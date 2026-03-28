package com.ticketapp.projetpi.dto;

public class LoginResponse {

    public String token;
    public String type = "Bearer";
    public long expiresIn;
    public Long userId;
    public String email;
    public String role;

    public LoginResponse(String token, long expiresIn, Long userId, String email, String role) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
        this.role = role;
    }
}