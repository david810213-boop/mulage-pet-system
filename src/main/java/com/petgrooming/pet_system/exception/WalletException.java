package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 儲值/錢包相關業務錯誤：儲值申請送出、審核、駁回、店家手動加值等。
 * 用在 TopUpService、WalletService。
 */
public class WalletException extends BusinessException {
    public WalletException(String message) {
        super(message);
    }

    public WalletException(String message, HttpStatus status) {
        super(message, status);
    }
}
