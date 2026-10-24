package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.DuplicateRequestException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.WalletException;
import com.petgrooming.pet_system.model.IdempotencyKey;
import com.petgrooming.pet_system.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 金流寫入的防重複送出。前端每份表單產生一個識別碼（API 用 Idempotency-Key 標頭，網頁表單用隱藏欄位），
 * 寫入與本次操作在同一個交易：操作失敗時識別碼跟著回滾，可以用同一個識別碼重試；
 * 操作成功後再用同一個識別碼送出會被擋下。沒有帶識別碼（舊版頁面）時不檢查，行為與原本相同。見 docs/adr/0008。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 64;
    private static final int RETENTION_DAYS = 7;

    private final IdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String action, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "請求識別碼格式不正確");
        }
        try {
            repository.saveAndFlush(IdempotencyKey.builder()
                    .key(key)
                    .action(action)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("重複送出已略過：action={}", action);
            throw new DuplicateRequestException();
        }
    }

    // 識別碼只需要擋短時間內的重送，保留一週後清除
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanup() {
        int deleted = repository.deleteCreatedBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("清除過期的請求識別碼 {} 筆", deleted);
        }
    }
}
