package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.SupplyUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SupplyUsageRecordRepository extends JpaRepository<SupplyUsageRecord, Long> {

    List<SupplyUsageRecord> findByUsedAtBetween(LocalDateTime start, LocalDateTime end);
}
