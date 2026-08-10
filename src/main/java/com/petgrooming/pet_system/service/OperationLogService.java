package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.OperationLog;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.OperationLogRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 操作紀錄服務：提供各業務 Service/Controller 呼叫的統一記錄方法，並負責每日清除逾期紀錄
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    private static final int RETENTION_DAYS = 60;

    // ── 已有完整 User 物件時使用（例如後台 Controller 已經查過 getLoginUser）───
    public void log(User actor, String category, String action, String targetDesc, String detail) {
        try {
            OperationLog entry = OperationLog.builder()
                    .actorUsername(actor != null ? actor.getUsername() : "system")
                    .actorName(actor != null ? actor.getName() : "系統")
                    .actorRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                    .category(category)
                    .action(action)
                    .targetDesc(targetDesc)
                    .detail(detail)
                    .createdAt(LocalDateTime.now())
                    .build();
            operationLogRepository.save(entry);
        } catch (Exception e) {
            // 記錄失敗不應該影響主要業務流程，僅印出警告
            log.warn("寫入操作紀錄失敗：category={}, action={}, 原因={}", category, action, e.getMessage());
        }
    }

    // ── 只有 username（例如 REST API Controller 從 JWT 拿到的字串）時使用，
    //    內部自動查出姓名／角色，呼叫端不用自己多查一次 User ─────────────────
    public void logByUsername(String username, String category, String action, String targetDesc, String detail) {
        User actor = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        log(actor, category, action, targetDesc, detail);
    }

    // ── 後台查詢 ──────────────────────────────────────────────────────
    public List<OperationLog> getAll() {
        return operationLogRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<OperationLog> getByCategory(String category) {
        return operationLogRepository.findByCategoryOrderByCreatedAtDesc(category);
    }

    // ── 每日凌晨 3 點自動清除超過 60 天的舊紀錄 ─────────────────────────
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        operationLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("已清除 {} 天前的操作紀錄（早於 {}）", RETENTION_DAYS, cutoff);
    }
}
