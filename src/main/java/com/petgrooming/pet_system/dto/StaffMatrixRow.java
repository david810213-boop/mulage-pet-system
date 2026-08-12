package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 積分項目統計矩陣：某位員工在每一種積分項目上的隻數/次數與換算積分，
 * 以及該員工這個月的總積分。用來組成「全員工 × 全項目」的大表格。
 */
@Data
@Builder
public class StaffMatrixRow {
    private Long staffId;
    private String staffName;
    private Map<PerformanceCategory, Double> countByCategory;  // 各項目換算後的隻數/次數
    private Map<PerformanceCategory, Double> pointsByCategory; // 各項目累積積分
    private double totalPoints; // 該員工當月總積分（不含 OTHER）
}
