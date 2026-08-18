package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.ClosedDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClosedDateRepository extends JpaRepository<ClosedDate, Long> {

    boolean existsByDate(LocalDate date);

    Optional<ClosedDate> findByDate(LocalDate date);

    void deleteByDate(LocalDate date);

    // 後台列表用：由今天起（含）之後的公休日，日期由近到遠排序
    List<ClosedDate> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);
}
