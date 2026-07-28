package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CoatType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 需求 2：店家後台設定寵物毛長
 */
@Data
public class SetCoatTypeRequest {
    @NotNull(message = "請選擇毛長")
    private CoatType coatType; // SHORT / MEDIUM / LONG
}
