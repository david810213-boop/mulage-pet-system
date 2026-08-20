package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
}
