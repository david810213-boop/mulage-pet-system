package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class PetRequest {

    @NotBlank(message = "毛孩名字不能為空")
    private String name;

    @NotNull(message = "毛孩類型不能為空")
    private PetType petType;            // DOG / CAT / OTHER

    @NotBlank(message = "品種不能為空")
    private String breed;

    @NotNull(message = "體重不能為空")
    @Positive(message = "體重必須大於 0")
    private Double weight;

    @NotNull(message = "年齡不能為空")
    @PositiveOrZero(message = "年齡不能為負數")
    private Integer age;

    // 新增欄位
    @NotNull(message = "請選擇毛長")
    private CoatType coatType;          // 由飼主填寫（短毛/中長毛/長毛）

    private Boolean hasSeparationAnxiety = false; // 是否有分離焦慮

    private String ownerPhone;          // 家長手機

    private String notes;               // 注意事項
    // 體型（sizeCategory）不由前端傳入，系統依 petType + weight 自動判斷
}
