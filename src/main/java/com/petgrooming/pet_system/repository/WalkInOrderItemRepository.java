package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.WalkInOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalkInOrderItemRepository extends JpaRepository<WalkInOrderItem, Long> {

    // 需求 6：撈出所有「經手人未填寫」的項目（operatorStaff 為 null）供後續補填
    List<WalkInOrderItem> findByOperatorStaffIsNull();

    // 需求 6：積分結算 —— 依經手人（實際員工）分組加總（排除未填寫）
    @Query("""
        select oi.operatorStaff.id, oi.operatorStaff.name, sum(oi.points), sum(oi.price), count(oi)
        from WalkInOrderItem oi
        where oi.operatorStaff is not null
        group by oi.operatorStaff.id, oi.operatorStaff.name
        order by sum(oi.points) desc
    """)
    List<Object[]> sumPointsByOperator();
}
