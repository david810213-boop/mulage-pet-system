package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 更新寵物體重（公斤）。 */
public record UpdatePetWeightRequest(
        @NotNull(message = "請提供有效的體重") @Positive(message = "請提供有效的體重") Double weight) {
}
