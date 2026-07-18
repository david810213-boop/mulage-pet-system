package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.WalkInOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalkInOrderRepository extends JpaRepository<WalkInOrder, Long> {

    // 所有現場單（新到舊）— 交易紀錄列表
    List<WalkInOrder> findAllByOrderByCreatedAtDesc();

    // 某會員的現場單
    List<WalkInOrder> findByMemberUsernameOrderByCreatedAtDesc(String username);
}
