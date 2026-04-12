package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // ── Generate ────────────────────────────────────────────────────────────

    public String generateToken(User user, String jti) {
        return Jwts.builder()
                .subject(user.getId().toString())           // sub = UUID
                .id(jti)                                   // jti = unique session ID
                .claim("email",    user.getEmail())
                .claim("username", user.getUsername())
                .claim("role",     user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }

    // ── Extractors ──────────────────────────────────────────────────────────

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    // kept for any legacy call still using username-based lookup
    public String extractUsername(String token) {
        return extractClaims(token).get("username", String.class);
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    // ── Validation ──────────────────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Private helper ──────────────────────────────────────────────────────

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}