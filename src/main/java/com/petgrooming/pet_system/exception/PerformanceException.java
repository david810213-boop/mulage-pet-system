package com.petgrooming.pet_system.exception;

/**
 * 績效/積分相關業務錯誤：積分結算、積分拆分、獎勵金級距設定等。
 * 用在 PerformanceService。
 */
public class PerformanceException extends BusinessException {
    public PerformanceException(String message) {
        super(message);
    }

    public PerformanceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
