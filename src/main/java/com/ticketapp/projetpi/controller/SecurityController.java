package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.entity.SecurityAlert;
import com.ticketapp.projetpi.repository.SecurityAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/security")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecurityController {

    private final SecurityAlertRepository alertRepository;

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        List<SecurityAlert> alerts = unreadOnly ? 
                alertRepository.findByIsReadFalseOrderByTimestampDesc() : 
                alertRepository.findAllByOrderByTimestampDesc();
                
        return ResponseEntity.ok(alerts.stream().map(this::mapToDto).collect(Collectors.toList()));
    }

    @PutMapping("/alerts/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        alertRepository.findById(id).ifPresent(alert -> {
            alert.setRead(true);
            alertRepository.save(alert);
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/alerts/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", alertRepository.countByIsReadFalse()));
    }

    private Map<String, Object> mapToDto(SecurityAlert alert) {
        return Map.of(
            "id", alert.getId(),
            "userEmail", alert.getUser().getEmail(),
            "username", alert.getUser().getUsername(),
            "anomalyType", alert.getAnomalyType(),
            "details", alert.getDetails(),
            "riskScore", alert.getRiskScore(),
            "timestamp", alert.getTimestamp(),
            "isRead", alert.isRead()
        );
    }
}
