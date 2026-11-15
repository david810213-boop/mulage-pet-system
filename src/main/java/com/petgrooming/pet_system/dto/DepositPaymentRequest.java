package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 顧客回報訂金匯款。金額由系統依當天尚未付清的訂金決定，不由前端傳入。 */
public record DepositPaymentRequest(
        @Schema(description = "訂金對應的預約日期", example = "2026-11-20")
        @NotNull(message = "請指定預約日期")
        LocalDate date,

        @Schema(description = "匯款帳號後五碼", example = "12345")
        @NotNull(message = "請填寫匯款帳號後五碼")
        @Pattern(regexp = "\\d{5}", message = "匯款帳號後五碼請填 5 位數字")
        String lastFiveDigits,

        @Schema(description = "轉帳時間或備註（選填）", example = "11/17 14:30 轉帳")
        @Size(max = 200, message = "備註最多 200 字")
        String remitNote) {
}
