package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 需求（追加）：矩陣式待補經手人表單。
 * 一列 = 一筆訂單（現場開單或預約現場開單）的其中一隻寵物，橫向欄位固定對應
 * {@link com.petgrooming.pet_system.enums.PerformanceCategory} 裡的服務分類。
 * 只有「本次消費有出現的項目」那一格才會有值可以選經手人，其餘顯示「無」。
 */
@Data
@Builder
public class PendingOperatorMatrixResponse {
    private String sourceLabel; // 「現場開單」或「預約現場開單」
    private String code;        // 「現場單#4」或「AP017」
    private String petName;
    private List<Cell> cells;   // 順序固定跟著 COLUMNS 走，前端逐一對應欄位

    @Data
    @Builder
    public static class Cell {
        private Long itemId;   // null = 這張單沒有這個項目（顯示「無」）
        private Integer price;
        private Double points;
    }
}
