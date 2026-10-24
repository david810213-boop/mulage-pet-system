package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.model.TopUpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopUpRequestRepository extends JpaRepository<TopUpRequest, Long> {

    // 顧客查自己的儲值申請（新到舊）
    List<TopUpRequest> findByUserUsernameOrderByCreatedAtDesc(String username);

    // 店家後台：依狀態撈申請（待核帳清單用 PENDING）
    List<TopUpRequest> findByStatusOrderByCreatedAtAsc(TopUpStatus status);

    // 手動綁定既有匯入資料——過戶這個會員名下的儲值申請
    List<TopUpRequest> findByUserId(Long userId);

    // 加鎖讀取（SELECT ... FOR UPDATE）：同一筆申請的確認入帳／駁回依序執行，
    // 讀回最新已提交的狀態，避免連點或兩位員工同時操作時重複入帳（見 docs/adr/0008）。
    @Query(value = "select status from topup_requests where id = :id for update", nativeQuery = true)
    Optional<String> lockStatusById(@Param("id") Long id);
}
