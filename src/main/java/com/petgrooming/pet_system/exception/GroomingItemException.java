package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 美容服務項目相關業務錯誤：服務項目建立/修改/刪除、項目代碼重複等。
 * 用在 GroomingServiceImpl。
 */
public class GroomingItemException extends BusinessException {
    public GroomingItemException(String message) {
        super(message);
    }

    public GroomingItemException(String message, HttpStatus status) {
        super(message, status);
    }
}
