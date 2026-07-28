package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.model.TopUpRequest;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 儲值申請回傳格式（顧客端與店家端共用）
 */
@Data
public class TopUpRequestResponse {
    private Long id;
    private String username;      // 申請人帳號（店家端清單顯示用）
    private String name;          // 申請人姓名（與 UserResponse / WalkInOrderResponse 命名慣例一致）
    private Integer amount;
    private String lastFiveDigits;
    private String remitNote;
    private TopUpStatus status;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private String rejectReason;

    public static TopUpRequestResponse from(TopUpRequest r) {
        TopUpRequestResponse res = new TopUpRequestResponse();
        res.setId(r.getId());
        res.setUsername(r.getUser().getUsername());
        res.setName(r.getUser().getName());
        res.setAmount(r.getAmount());
        res.setLastFiveDigits(r.getLastFiveDigits());
        res.setRemitNote(r.getRemitNote());
        res.setStatus(r.getStatus());
        res.setStatusLabel(r.getStatus().getLabel());
        res.setCreatedAt(r.getCreatedAt());
        res.setReviewedAt(r.getReviewedAt());
        res.setReviewedBy(r.getReviewedBy());
        res.setRejectReason(r.getRejectReason());
        return res;
    }
}
