package com.petgrooming.pet_system.exception;

/**
 * 同一個請求識別碼（Idempotency-Key）已經處理過：前一次送出已成功，這次不再重複執行。
 */
public class DuplicateRequestException extends BusinessException {
    public DuplicateRequestException() {
        super(ErrorCode.CONFLICT, "此筆已送出，請勿重複送出");
    }
}
