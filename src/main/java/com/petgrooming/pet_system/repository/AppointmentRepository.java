package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // 查某使用者的所有預約（對應原本 getReceiptsByUser）
    List<Appointment> findByUserUsername(String username);

    // 查某天的所有預約（用來計算已佔用時段）
    List<Appointment> findByDate(LocalDate date);

    // 查某天已結帳的預約（績效日報下拉選單用）
    List<Appointment> findByDateAndPaidTrue(LocalDate date);

    // 查未付款的預約（結帳用）
    List<Appointment> findByUserUsernameAndPaidFalse(String username);

    // 確認時段是否已被預約（避免重疊）
    boolean existsByDateAndStartTimeLessThanAndEndTimeGreaterThan(
        LocalDate date, LocalTime endTime, LocalTime startTime
    );
}