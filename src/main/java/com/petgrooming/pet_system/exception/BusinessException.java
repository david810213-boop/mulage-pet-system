package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 全站業務例外的共同基底類別，依業務領域分成子類別（預約、付款、錢包、會員、寵物、庫存等）。
 * 帶一個 {@link ErrorCode}（預設 {@link ErrorCode#BUSINESS_RULE}），決定 API 回應的狀態碼與 code 欄位。
 * 例外訊息會直接顯示給使用者，必須是使用者看得懂的中文說明。
 *
 * 繼承 {@link IllegalArgumentException} 而非 RuntimeException 是刻意的：
 * 網頁版與手機版的 MVC controller 有上百處以 {@code catch (IllegalArgumentException e)}
 * 接住業務錯誤、在原畫面顯示訊息；改繼承 RuntimeException 會讓這些錯誤變成 500 錯誤頁。
 * 這也是為什麼 API 例外處理只對 BusinessException 回傳原始訊息，
 * 其他 IllegalArgumentException（例如函式庫丟出的）一律回通用訊息，避免內部細節外洩。
 */
public class BusinessException extends IllegalArgumentException {

    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_RULE, message);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.status();
    }
}
