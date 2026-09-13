package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 登入/身分驗證相關業務錯誤：LINE idToken 驗證、店員綁定 LINE、
 * 帳號密碼登入等。用在 LineBindService、AuthMvcController 相關邏輯。
 *
 * 預設狀態碼用 401 Unauthorized（跟 BusinessException 預設的 400 不同），
 * 因為這類錯誤本質上是「身分驗證失敗」，不是單純的輸入格式錯誤。
 */
public class AuthException extends BusinessException {
    public AuthException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public AuthException(String message, HttpStatus status) {
        super(message, status);
    }
}
