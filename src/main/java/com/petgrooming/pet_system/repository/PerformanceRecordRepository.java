package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.PerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, Long> {

    // 查詢某員工某月的所有績效紀錄
    List<PerformanceRecord> findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(
            Long staffId, LocalDate start, LocalDate end);

    // 查詢某預約的所有績效紀錄
    List<PerformanceRecord> findByAppointmentId(Long appointmentId);

    // 查詢某現場單的所有績效紀錄（需求：退款時要能一次刪除這張單的所有積分）
    List<PerformanceRecord> findByWalkInOrderId(Long walkInOrderId);

    // 查詢某日所有員工績效
    List<PerformanceRecord> findByServiceDate(LocalDate date);

    // 查詢某月所有員工績效（用於月底結算）
    @Query("SELECT p FROM PerformanceRecord p WHERE p.serviceDate >= :start AND p.serviceDate <= :end")
    List<PerformanceRecord> findByMonth(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // 需求 12：拆分歷史——查出所有「因拆分而產生」的紀錄（splitFromRecordId 不為 null）
    List<PerformanceRecord> findBySplitFromRecordIdIsNotNullAndServiceDateBetweenOrderByServiceDateDesc(
            LocalDate start, LocalDate end);

    // 需求 12：不限日期範圍，查出全部「拆分產生的紀錄」，用來判斷哪些原始紀錄已經被拆過
    List<PerformanceRecord> findBySplitFromRecordIdIsNotNull();

    // 需求 12：判斷某筆紀錄是否已經被拆分過（是否已有以它為來源的拆分紀錄）
    boolean existsBySplitFromRecordId(Long id);
}
