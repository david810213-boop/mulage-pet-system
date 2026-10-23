package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotNull;

/** 鎖定寵物固定套餐。 */
public record LockGroomingItemRequest(
        @NotNull(message = "請提供 groomingItemId") Long groomingItemId) {
}
