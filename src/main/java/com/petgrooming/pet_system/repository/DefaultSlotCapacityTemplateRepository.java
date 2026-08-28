package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.DefaultSlotCapacityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DefaultSlotCapacityTemplateRepository extends JpaRepository<DefaultSlotCapacityTemplate, Long> {

    Optional<DefaultSlotCapacityTemplate> findBySlotTime(LocalTime slotTime);

    List<DefaultSlotCapacityTemplate> findAllByOrderBySlotTimeAsc();
}
