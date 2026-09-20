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

    // 需求（追加，2026-09-08）：手動綁定既有匯入資料——過戶這個會員名下所有預約
    List<Appointment> findByUserId(Long userId);

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

    // 需求（修正，2026-09-13）：N+1 查詢優化。getAllForAdmin()/getAllAppointments()
    // 原本用 findAll() 撈全部預約，DTO 轉換時逐筆存取 a.getUser()（LAZY）跟
    // a.getSelectedItems()（雖然是 EAGER，但預設用逐筆 SELECT 撈，不是 JOIN），
    // 資料一多，每次載入預約列表都會觸發幾百條額外 SQL。改用 JOIN FETCH 把
    // user、selectedItems 併進同一條查詢一次撈出來，只需要 1 條 SQL（selectedItems
    // 是多對多，JOIN 之後同一筆預約會因為對應多個項目重複出現，用 DISTINCT
    // 讓 Hibernate 在組裝結果時把同一筆預約的重複列合併回一筆）。
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.selectedItems " +
            "ORDER BY a.id DESC")
    List<Appointment> findAllWithUserAndItems();
}