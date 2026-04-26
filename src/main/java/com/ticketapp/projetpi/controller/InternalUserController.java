package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.dto.UserResponse;
import com.ticketapp.projetpi.entity.Role;
import com.ticketapp.projetpi.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint INTERNE réservé aux appels inter-microservices (via Eureka/Feign).
 * ── PAS de sécurité ici — la Gateway bloque /internal/** de l'extérieur.
 *
 * Cet endpoint permet aux autres services (forum, booking, etc.)
 * de récupérer les informations d'un utilisateur par son UUID.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /internal/users/{id}
     * Retourne les informations publiques d'un utilisateur.
     * Accessible uniquement en interne (le gateway bloque /internal/**)
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
    @GetMapping(value = "/internal/users", params = "role")
    public ResponseEntity<List<UserResponse>> getUsersByRole(
            @RequestParam(required = false) String role) {
        if (role != null && !role.isBlank()) {
            Role roleEnum = Role.valueOf(role.toUpperCase());
            return ResponseEntity.ok(userService.getUsersByRole(roleEnum));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

}
