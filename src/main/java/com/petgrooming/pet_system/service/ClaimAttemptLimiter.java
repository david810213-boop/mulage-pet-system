package com.petgrooming.pet_system.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 需求（追加，2026-09-30）：新客報到「電話查詢」與「認領確認」的次數限制。
 *
 * 每個 LINE 帳號（username）在 10 分鐘內最多 5 次，查詢與認領共用額度；
 * 認領成功就清掉紀錄。存在記憶體裡，重新部署會歸零——目前 Railway 只跑一個
 * 執行個體，這個強度已經足夠擋掉「一直換電話號碼試」的行為。
 */
@Component
public class ClaimAttemptLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Deque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_ATTEMPTS) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }
}
