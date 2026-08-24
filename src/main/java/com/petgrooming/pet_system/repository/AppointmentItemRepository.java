package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.AppointmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentItemRepository extends JpaRepository<AppointmentItem, Long> {

    List<AppointmentItem> findByAppointmentId(Long appointmentId);

    // 需求（追加，2026-08-26 修正）：預約列表頁批次查多筆預約的核對後項目，
    // 避免一筆一筆查造成 N+1 查詢問題（列表頁可能同時顯示幾十筆預約）。
    List<AppointmentItem> findByAppointmentIdIn(java.util.List<Long> appointmentIds);

    // 待補經手人清單（operatorStaff 為 null 的項目）
    List<AppointmentItem> findByOperatorStaffIsNull();

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
