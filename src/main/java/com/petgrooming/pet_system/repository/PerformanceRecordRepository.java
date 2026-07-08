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

    // 查詢某日所有員工績效
    List<PerformanceRecord> findByServiceDate(LocalDate date);

    // 查詢某月所有員工績效（用於月底結算）
    @Query("SELECT p FROM PerformanceRecord p WHERE p.serviceDate >= :start AND p.serviceDate <= :end")
    List<PerformanceRecord> findByMonth(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
