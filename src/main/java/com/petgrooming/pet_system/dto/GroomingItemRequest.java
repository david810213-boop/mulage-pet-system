package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GroomingItemRequest {
    
    @NotBlank(message = "項目代碼不能為空")
    @Pattern(regexp = "^GS\\d{3}$", message = "項目代碼格式必須為 GS 開頭加上 3 位數字，例如 GS013")
    private String itemCode;

    @NotBlank(message = "美容項目名稱不能為空")
    private String name;

    private String description;

    @NotNull(message = "價格不能為空")
    @Positive(message = "價格必須大於 0")
    private Double price;

    // 需求 4：是否可線上預約（大美容/小美容/精緻洗/定製洗 = true）
    private Boolean bookable = false;

}