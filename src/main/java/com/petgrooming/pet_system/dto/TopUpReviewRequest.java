package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 店家核帳（駁回時帶原因）
 */
@Data
public class TopUpReviewRequest {
    private String rejectReason; // 僅駁回時使用
}
