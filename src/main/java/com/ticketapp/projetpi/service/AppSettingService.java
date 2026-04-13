package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.entity.AppSetting;
import com.ticketapp.projetpi.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository repository;

    public AppSetting getSettings() {
        return repository.findCurrentSetting()
                .orElseGet(() -> {
                    AppSetting defaultSetting = new AppSetting();
                    return repository.save(defaultSetting);
                });
    }

    @Transactional
    public AppSetting updateSettings(AppSetting newSettings) {
        AppSetting current = getSettings();
        
        current.setAppName(newSettings.getAppName());
        current.setLogoUrl(newSettings.getLogoUrl());
        current.setFaviconUrl(newSettings.getFaviconUrl());
        current.setDefaultLanguage(newSettings.getDefaultLanguage());
        current.setTimeZone(newSettings.getTimeZone());
        current.setDateFormat(newSettings.getDateFormat());
        current.setPrimaryColor(newSettings.getPrimaryColor());
        current.setSecondaryColor(newSettings.getSecondaryColor());
        current.setFontFamily(newSettings.getFontFamily());
        current.setThemeMode(newSettings.getThemeMode());
        
        return repository.save(current);
    }
}
