package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 鎖定寵物的固定組合：直接指定項目與固定價，或從一張單的服務項目鎖定，三者擇一。 */
public record LockComboRequest(
        @Schema(description = "直接指定的組合項目（一項精緻洗＋任意加購）") @Valid List<ComboItem> items,
        @Schema(description = "現場單編號") Long walkInOrderId,
        @Schema(description = "預約編號") Long appointmentId) {

    /** 組合裡的一項。 */
    public record ComboItem(
            @Schema(description = "服務項目 id") @NotNull Long groomingItemId,
            @Schema(description = "固定價") @NotNull @Min(0) Integer price) {
    }
}
