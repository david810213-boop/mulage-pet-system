package com.petgrooming.pet_system.repository;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // 查某使用者的所有預約
    List<Appointment> findByUserUsername(String username);

    // 手動綁定既有匯入資料——過戶這個會員名下所有預約
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

    // 查某天尚未發送過提醒、且已確認的預約（排程用）
    List<Appointment> findByDateAndReminderSentFalseAndStatus(
        LocalDate date, AppointmentStatus status
    );

    // 查某會員名下、某寵物名稱的所有已結帳預約（用來回溯上次洗澡日期）
    // 注意：Appointment 沒有直接關聯 Pet 實體（只存 petName 快照），
    // 沿用既有的「(會員, 寵物名) 配對識別同一隻寵物」慣例。
    List<Appointment> findByUserIdAndPetNameAndPaidTrue(Long userId, String petName);

    // 同一隻毛孩是否已經有一筆還沒完成的「初次・現場評估」預約
    List<Appointment> findByUserIdAndPetNameAndFirstVisitAssessmentTrueAndPaidFalse(Long userId, String petName);

    // 用 JOIN FETCH 把 user、selectedItems 併進同一條查詢（避免 N+1）。selectedItems 是多對多，
    // JOIN 之後同一筆預約會重複出現，用 DISTINCT 讓 Hibernate 合併回一筆。
    @Query(
            "SELECT DISTINCT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.selectedItems " +
            "ORDER BY a.id DESC")
    List<Appointment> findAllWithUserAndItems();

    // 後台只需要一部分預約時（某段日期、待確認、單筆），不要撈全部歷史再在記憶體篩選；
    // 跟 findAllWithUserAndItems() 一樣 JOIN FETCH 使用者與選擇項目，一條 SQL 查完。
    @Query(
            "SELECT DISTINCT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.selectedItems " +
            "WHERE a.date BETWEEN :from AND :to " +
            "ORDER BY a.id DESC")
    List<Appointment> findWithUserAndItemsByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(
            "SELECT DISTINCT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.selectedItems " +
            "WHERE a.status = :status AND a.date >= :from " +
            "ORDER BY a.id DESC")
    List<Appointment> findWithUserAndItemsByStatusFrom(@Param("status") AppointmentStatus status,
                                                       @Param("from") LocalDate from);

    @Query(
            "SELECT DISTINCT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.selectedItems " +
            "WHERE a.id = :id")
    Optional<Appointment> findWithUserAndItemsById(@Param("id") Long id);

    // 加鎖讀取（SELECT ... FOR UPDATE）：同一筆的結帳／確認收款／退款依序執行。
    // 只讀回判斷用的欄位：鎖定讀取一定讀到最新已提交的值，不受快取中的舊實體與交易快照影響（見 docs/adr/0008）。
    @Query(value = "select paid from appointments where id = :id for update", nativeQuery = true)
    Optional<Boolean> lockPaidById(@Param("id") Long id);
}
