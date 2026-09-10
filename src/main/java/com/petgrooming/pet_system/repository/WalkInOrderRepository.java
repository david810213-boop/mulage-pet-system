package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.WalkInOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalkInOrderRepository extends JpaRepository<WalkInOrder, Long> {

    // 所有現場單（新到舊）— 交易紀錄列表
    List<WalkInOrder> findAllByOrderByCreatedAtDesc();

    // 需求 8：查某會員名下、某寵物名稱的所有已結帳現場單（現場開單有會員時，同樣列入回洗優惠歷史計算）
    List<WalkInOrder> findByMemberIdAndPetNameAndPaidTrue(Long memberId, String petName);

    // 某會員的現場單
    List<WalkInOrder> findByMemberUsernameOrderByCreatedAtDesc(String username);

    // 需求（追加，2026-09-08）：手動綁定既有匯入資料——過戶這個會員名下所有現場單
    List<WalkInOrder> findByMemberId(Long memberId);
}
