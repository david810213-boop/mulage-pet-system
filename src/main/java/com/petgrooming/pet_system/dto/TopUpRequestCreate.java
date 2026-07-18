package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 顧客送出線上轉帳儲值申請的輸入
 */
@Data
public class TopUpRequestCreate {

    @NotNull(message = "儲值金額不能為空")
    @Min(value = 1, message = "儲值金額至少 1 元")
    private Integer amount;

    // 匯款帳號後五碼（核帳比對用，選填但強烈建議填）
    private String lastFiveDigits;

    // 轉帳時間 / 備註
    private String remitNote;
}
