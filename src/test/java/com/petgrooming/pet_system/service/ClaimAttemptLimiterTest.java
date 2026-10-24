package com.petgrooming.pet_system.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 自助認領的查詢／認領次數限制：同一帳號 10 分鐘內最多 5 次，認領成功後重置。 */
class ClaimAttemptLimiterTest {

    private final ClaimAttemptLimiter limiter = new ClaimAttemptLimiter();

    @Test
    void allowsFiveAttemptsThenBlocks() {
        for (int i = 0; i < ClaimAttemptLimiter.MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryAcquire("line-user")).isTrue();
        }
        assertThat(limiter.tryAcquire("line-user")).isFalse();
    }

    @Test
    void limitIsPerAccount() {
        for (int i = 0; i < ClaimAttemptLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryAcquire("user-a");
        }

        assertThat(limiter.tryAcquire("user-b")).isTrue();
    }

    @Test
    void resetClearsTheCounter() {
        for (int i = 0; i < ClaimAttemptLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryAcquire("line-user");
        }

        limiter.reset("line-user");

        assertThat(limiter.tryAcquire("line-user")).isTrue();
    }
}
