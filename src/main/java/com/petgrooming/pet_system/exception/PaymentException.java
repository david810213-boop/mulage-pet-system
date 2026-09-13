package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 結帳/交易相關業務錯誤：結帳、折扣計算、財務報表等。用在 PaymentService。
 *
 * ⚠️ 目前 PaymentService 尚未遷移到這個例外類別（throw 的地方有 10 處，
 * 這次先不動），先建立類別，之後可以逐步遷移。
 */
public class PaymentException extends BusinessException {
    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, HttpStatus status) {
        super(message, status);
    }
}
