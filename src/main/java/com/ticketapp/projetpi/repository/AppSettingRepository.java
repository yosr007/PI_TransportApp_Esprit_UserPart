package com.ticketapp.projetpi.repository;

import com.ticketapp.projetpi.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {
    default Optional<AppSetting> findCurrentSetting() {
        return findById(1L);
    }
}
