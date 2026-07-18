package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.model.TopUpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopUpRequestRepository extends JpaRepository<TopUpRequest, Long> {

    // 顧客查自己的儲值申請（新到舊）
    List<TopUpRequest> findByUserUsernameOrderByCreatedAtDesc(String username);

    // 店家後台：依狀態撈申請（待核帳清單用 PENDING）
    List<TopUpRequest> findByStatusOrderByCreatedAtAsc(TopUpStatus status);
}
