package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.petgrooming.pet_system.enums.CoatType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 店家後台設定寵物毛長
 */
@Data
public class SetCoatTypeRequest {
    @NotNull(message = "請選擇毛長")
    @Schema(description = "SHORT / MEDIUM / LONG")
    private CoatType coatType;
}
