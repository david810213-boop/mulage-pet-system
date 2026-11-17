package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentItemRepository extends JpaRepository<AppointmentItem, Long> {

    List<AppointmentItem> findByAppointmentId(Long appointmentId);

    List<AppointmentItem> findByAppointmentIdIn(Collection<Long> appointmentIds);

    // 預約列表頁批次查多筆預約的核對後項目，
    // 避免一筆一筆查造成 N+1 查詢問題（列表頁可能同時顯示幾十筆預約）。
    List<AppointmentItem> findByAppointmentIdIn(List<Long> appointmentIds);

    // 待補經手人清單（operatorStaff 為 null 的項目）
    List<AppointmentItem> findByOperatorStaffIsNull();

    // 可以事後補第二經手人的項目：已有第一位、還沒有第二位、有積分可分
    @Query("""
        select ai from AppointmentItem ai
        join fetch ai.appointment a
        join fetch ai.operatorStaff
        where ai.operatorStaff2 is null and ai.points > 0
          and ai.performanceCategory <> :other and a.date >= :from
        order by a.date desc, ai.id
    """)
    List<AppointmentItem> findSecondOperatorCandidates(@Param("from") LocalDate from,
                                                       @Param("other") PerformanceCategory other);

    // 加鎖讀取：補第二經手人時，同一個項目連點或兩人同時操作只會成功一次
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ai from AppointmentItem ai where ai.id = :id")
    Optional<AppointmentItem> lockById(@Param("id") Long id);

    // 積分結算 —— 依經手人（實際員工）分組加總（排除未填寫）
    @Query("""
        select ai.operatorStaff.id, ai.operatorStaff.name, sum(ai.points), sum(ai.price), count(ai)
        from AppointmentItem ai
        where ai.operatorStaff is not null
        group by ai.operatorStaff.id, ai.operatorStaff.name
        order by sum(ai.points) desc
    """)
    List<Object[]> sumPointsByOperator();
}
