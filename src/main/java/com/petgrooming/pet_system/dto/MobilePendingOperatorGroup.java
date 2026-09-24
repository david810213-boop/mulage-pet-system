package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 員工手機版「待補經手人」：一張單（預約或現場單）底下還沒填經手人的項目（需求，2026-09-24 第六批）。
 * 資料來源跟網頁版待補經手人矩陣相同（未填經手人、且有積分的項目），
 * 只是改成一張單一張卡片、一個項目一列，手機比較好操作。
 */
@Data
@Builder
public class MobilePendingOperatorGroup {
    private String kind;        // A = 預約（AppointmentItem）、W = 現場單（WalkInOrderItem）
    private Long orderId;       // 預約 id 或現場單 id
    private String code;        // 「AP017」或「現場單#4」
    private String petName;
    private LocalDate date;     // 預約日期／現場單開單日期，排序用
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private Long itemId;
        private String itemName;
        private String categoryLabel;
        private int price;
        private double points;
    }
}
