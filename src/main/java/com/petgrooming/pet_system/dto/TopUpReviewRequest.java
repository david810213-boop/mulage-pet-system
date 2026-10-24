package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 店家核帳（駁回時帶原因）
 */
@Data
public class TopUpReviewRequest {
    @Schema(description = "僅駁回時使用")
    private String rejectReason;
}
