package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 會員/帳號相關業務錯誤：既有資料匯入、LINE 自助認領、店家手動綁定、
 * 會員基本資料、備注等。用在 MemberImportService、UserService。
 */
public class MemberException extends BusinessException {
    public MemberException(String message) {
        super(message);
    }

    public MemberException(String message, HttpStatus status) {
        super(message, status);
    }
}
