package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.AppointmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentItemRepository extends JpaRepository<AppointmentItem, Long> {

    List<AppointmentItem> findByAppointmentId(Long appointmentId);

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
