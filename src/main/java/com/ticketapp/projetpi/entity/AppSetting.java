package com.ticketapp.projetpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_settings")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppSetting {

    @Id
    private Long id = 1L; // Hardcode ID 1 for singleton pattern

    // General Settings
    private String appName = "Wassalni";
    private String logoUrl = "";
    private String faviconUrl;
    private String defaultLanguage = "en";
    private String timeZone = "UTC";
    private String dateFormat = "yyyy-MM-dd";

    // Design & Styling
    private String primaryColor = "#6366f1";
    private String secondaryColor = "#8b5cf6";
    private String fontFamily = "Inter";
    private String themeMode = "LIGHT"; // LIGHT, DARK
}
