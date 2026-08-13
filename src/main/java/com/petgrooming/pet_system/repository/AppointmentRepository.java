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

    // 需求 13：查某天尚未發送過提醒、且已確認的預約（排程用）
    List<Appointment> findByDateAndReminderSentFalseAndStatus(
        LocalDate date, com.petgrooming.pet_system.enums.AppointmentStatus status
    );

    // 需求 8：查某會員名下、某寵物名稱的所有已結帳預約（用來回溯上次洗澡日期）
    // 注意：Appointment 沒有直接關聯 Pet 實體（只存 petName 快照），
    // 沿用需求 9 既有的「(會員, 寵物名) 配對識別同一隻寵物」慣例。
    List<Appointment> findByUserIdAndPetNameAndPaidTrue(Long userId, String petName);
}