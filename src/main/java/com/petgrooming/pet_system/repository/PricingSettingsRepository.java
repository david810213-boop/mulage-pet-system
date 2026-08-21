package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
    // 需求（追加）：同 CompanySignatureRepository，避免 findAll().findFirst() 順序不固定
    Optional<PricingSettings> findFirstByOrderByIdDesc();
}
