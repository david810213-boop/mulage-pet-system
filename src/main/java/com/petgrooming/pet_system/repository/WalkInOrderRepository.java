package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.WalkInOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalkInOrderRepository extends JpaRepository<WalkInOrder, Long> {

    // 所有現場單（新到舊）— 交易紀錄列表
    List<WalkInOrder> findAllByOrderByCreatedAtDesc();

    // 查某會員名下、某寵物名稱的所有已結帳現場單（現場開單有會員時，同樣列入回洗優惠歷史計算）
    List<WalkInOrder> findByMemberIdAndPetNameAndPaidTrue(Long memberId, String petName);

    // 某會員的現場單
    List<WalkInOrder> findByMemberUsernameOrderByCreatedAtDesc(String username);

    // 手動綁定既有匯入資料——過戶這個會員名下所有現場單
    List<WalkInOrder> findByMemberId(Long memberId);

    // 首頁今日營收：只查付款時間在指定區間內的已付款現場單，不撈全部歷史
    List<WalkInOrder> findByPaidTrueAndPaymentTimeGreaterThanEqualAndPaymentTimeLessThan(LocalDateTime from, LocalDateTime to);

    // 加鎖讀取（SELECT ... FOR UPDATE）：同一筆的結帳／確認收款／退款依序執行。
    // 只讀回判斷用的欄位：鎖定讀取一定讀到最新已提交的值，不受快取中的舊實體與交易快照影響（見 docs/adr/0008）。
    @Query(value = "select paid from walk_in_orders where id = :id for update", nativeQuery = true)
    Optional<Boolean> lockPaidById(@Param("id") Long id);
}
