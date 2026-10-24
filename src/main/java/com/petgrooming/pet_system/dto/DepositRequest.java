package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.petgrooming.pet_system.config.StoreRules;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepositRequest {

    @NotNull(message = "儲值金額不能為空")
    @Min(value = StoreRules.MIN_DEPOSIT, message = "單筆儲值最低 $5,000")
    private Integer amount;

    @Schema(description = "備註（選填）")
    private String note;

    // 防重複送出的識別碼，見 IdempotencyService
    private String idempotencyKey;
}
