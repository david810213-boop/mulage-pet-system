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

    // 加鎖讀取（SELECT ... FOR UPDATE）：鎖定讀取永遠讀最新已提交資料，
    // 看得到預約流程在獨立交易（ensureSlot）裡剛建立的時段列。
    // 實體若已在本交易快取中仍會回傳舊值，呼叫端須再 refresh（見 SlotCapacityService）。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SlotCapacity s where s.slotDate = :d and s.slotTime = :t")
    Optional<SlotCapacity> lockSlot(@Param("d") LocalDate d, @Param("t") LocalTime t);
}
