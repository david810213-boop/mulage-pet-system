package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.MonthlyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyPerformanceRepository extends JpaRepository<MonthlyPerformance, Long> {

    Optional<MonthlyPerformance> findByStaffIdAndYearMonth(Long staffId, LocalDate yearMonth);

    List<MonthlyPerformance> findByYearMonthOrderByTotalPointsDesc(LocalDate yearMonth);

    List<MonthlyPerformance> findByStaffIdOrderByYearMonthDesc(Long staffId);
}
