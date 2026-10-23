package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.SlotCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotCapacityRepository extends JpaRepository<SlotCapacity, Long> {

    // 一般查詢（不加鎖）：計算剩餘名額顯示用
    Optional<SlotCapacity> findBySlotDateAndSlotTime(LocalDate slotDate, LocalTime slotTime);

    List<SlotCapacity> findBySlotDate(LocalDate slotDate);
}
