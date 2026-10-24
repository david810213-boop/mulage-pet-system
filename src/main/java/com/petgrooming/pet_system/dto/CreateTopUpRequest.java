package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "儲值金額，單筆最低 5,000", example = "8000")
    private Integer amount;

    @Schema(description = "匯款帳號後五碼（核帳比對用，選填但強烈建議填）", example = "12345")
    private String lastFiveDigits;

    @Schema(description = "轉帳時間或備註", example = "01/15 14:30")
    private String remitNote;

    @Schema(description = "防重複送出的識別碼；一般由 Idempotency-Key 標頭帶入，見 IdempotencyService")
    private String idempotencyKey;
}
