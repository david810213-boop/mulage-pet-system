package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * API 錯誤代碼：放在錯誤回應的 code 欄位，讓前端依代碼判斷，不必比對中文訊息。
 * 每個代碼對應一個 HTTP 狀態碼。
 */
public enum ErrorCode {
    /** 請求欄位驗證失敗（必填、格式、範圍）。 */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    /** 請求本身無法解析（JSON 格式錯誤、參數型別錯誤）。 */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    /** 違反業務規則（額滿、餘額不足、狀態不允許等）。 */
    BUSINESS_RULE(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    /** 與既有資料衝突（重複、仍被其他資料參照）。 */
    CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
