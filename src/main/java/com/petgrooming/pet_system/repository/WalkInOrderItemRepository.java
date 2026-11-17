package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalkInOrderItemRepository extends JpaRepository<WalkInOrderItem, Long> {

    // 撈出所有「經手人未填寫」的項目（operatorStaff 為 null）供後續補填
    List<WalkInOrderItem> findByOperatorStaffIsNull();

    // 可以事後補第二經手人的項目：已有第一位、還沒有第二位、有積分可分
    @Query("""
        select oi from WalkInOrderItem oi
        join fetch oi.order o
        join fetch oi.operatorStaff
        where oi.operatorStaff2 is null and oi.points > 0
          and oi.performanceCategory <> :other and o.createdAt >= :from
        order by o.createdAt desc, oi.id
    """)
    List<WalkInOrderItem> findSecondOperatorCandidates(@Param("from") LocalDateTime from,
                                                       @Param("other") PerformanceCategory other);

    // 加鎖讀取：補第二經手人時，同一個項目連點或兩人同時操作只會成功一次
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oi from WalkInOrderItem oi where oi.id = :id")
    Optional<WalkInOrderItem> lockById(@Param("id") Long id);

    // 積分結算 —— 依經手人（實際員工）分組加總（排除未填寫）
    @Query("""
        select oi.operatorStaff.id, oi.operatorStaff.name, sum(oi.points), sum(oi.price), count(oi)
        from WalkInOrderItem oi
        where oi.operatorStaff is not null
        group by oi.operatorStaff.id, oi.operatorStaff.name
        order by sum(oi.points) desc
    """)
    List<Object[]> sumPointsByOperator();
}
