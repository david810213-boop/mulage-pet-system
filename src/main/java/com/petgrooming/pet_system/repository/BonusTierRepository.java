package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.BonusTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BonusTierRepository extends JpaRepository<BonusTier, Long> {
    List<BonusTier> findAllByOrderByMinPointsAsc();
}
