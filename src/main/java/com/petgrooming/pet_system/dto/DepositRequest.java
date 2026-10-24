package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.config.StoreRules;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepositRequest {

    @NotNull(message = "儲值金額不能為空")
    @Min(value = StoreRules.MIN_DEPOSIT, message = "單筆儲值最低 $5,000")
    private Integer amount;

    private String note; // 備註（選填）
}
