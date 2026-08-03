package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.ClinicSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, Long> {
}
