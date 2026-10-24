package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.config.StoreRules;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 顧客送出線上轉帳儲值申請的輸入
 */
@Data
public class CreateTopUpRequest {

    @NotNull(message = "儲值金額不能為空")
    @Min(value = StoreRules.MIN_DEPOSIT, message = "單筆儲值最低 $5,000")
    private Integer amount;

    // 匯款帳號後五碼（核帳比對用，選填但強烈建議填）
    private String lastFiveDigits;

    // 轉帳時間 / 備註
    private String remitNote;

    // 防重複送出的識別碼（由 Idempotency-Key 標頭帶入），見 IdempotencyService
    private String idempotencyKey;
}
