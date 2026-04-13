package com.ticketapp.projetpi.controller;

import com.ticketapp.projetpi.entity.AppSetting;
import com.ticketapp.projetpi.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final AppSettingService settingService;

    @GetMapping
    public ResponseEntity<AppSetting> getSettings() {
        return ResponseEntity.ok(settingService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppSetting> updateSettings(@RequestBody AppSetting settings) {
        return ResponseEntity.ok(settingService.updateSettings(settings));
    }
}
