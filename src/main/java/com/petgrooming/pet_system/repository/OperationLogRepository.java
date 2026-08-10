package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    // 後台查詢用：依時間新到舊
    List<OperationLog> findAllByOrderByCreatedAtDesc();

    List<OperationLog> findByCategoryOrderByCreatedAtDesc(String category);

    List<OperationLog> findByActorUsernameOrderByCreatedAtDesc(String actorUsername);

    List<OperationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    // 排程清除用：刪除超過保留期限（60 天）的舊紀錄
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
