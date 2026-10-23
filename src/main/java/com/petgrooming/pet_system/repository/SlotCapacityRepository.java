package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.SlotCapacity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 加鎖讀取（SELECT ... FOR UPDATE）。呼叫端須先移除快取中的同一實體，見 SlotCapacityService.lockSlot
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SlotCapacity s where s.slotDate = :d and s.slotTime = :t")
    Optional<SlotCapacity> lockSlot(@Param("d") LocalDate d, @Param("t") LocalTime t);
}
