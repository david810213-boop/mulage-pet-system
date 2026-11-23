package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 把一張單的服務項目鎖定成寵物的固定組合，兩個欄位擇一填。 */
public record LockComboRequest(
        @Schema(description = "現場單編號") Long walkInOrderId,
        @Schema(description = "預約編號") Long appointmentId) {
}
