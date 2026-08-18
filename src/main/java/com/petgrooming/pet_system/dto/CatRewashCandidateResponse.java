package com.petgrooming.pet_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * 需求 8-2：貓咪回洗優惠名單，供店家篩選＋手動聯繫（匯出 Excel/CSV）用。
 */
@Data
@AllArgsConstructor
public class CatRewashCandidateResponse {
    private Long petId;
    private String petName;
    private String ownerName;
    private String ownerPhone;      // Pet.ownerPhone 快照，避免又要多查一次 User
    private LocalDate lastBathDate; // 從未洗過則為 null
    private Long daysSinceLastBath; // 從未洗過則為 null
    private boolean withinDiscountWindow; // 是否仍在 90 天優惠期內
}
