package com.ticketapp.projetpi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import com.ticketapp.projetpi.service.SessionService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final SessionService sessionService;

    public SecurityConfig(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.disable()) // 👈 désactivé car déjà géré par l'API Gateway
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/webauthn/login/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:9090"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> withJti = new SessionValidator(sessionService);
        OAuth2TokenValidator<Jwt> withTimestamp = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withTimestamp, withJti);

        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    private static class SessionValidator implements OAuth2TokenValidator<Jwt> {
        private final SessionService sessionService;

        public SessionValidator(SessionService sessionService) {
            this.sessionService = sessionService;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String jti = jwt.getId();
            if (jti != null && sessionService.isSessionActive(jti)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session revoked", null));
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object roles = jwt.getClaim("roles");
            if (roles == null) {
                roles = jwt.getClaim("role");
            }
            if (roles instanceof String role) {
                if (!role.startsWith("ROLE_")) {
                    role = "ROLE_" + role;
                }
                return List.of(new SimpleGrantedAuthority(role));
            } else if (roles instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> roleCollection = (Collection<String>) roles;
                return roleCollection.stream()
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}