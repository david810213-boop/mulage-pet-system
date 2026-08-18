package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 會員信息頁「消費記錄」用的統一格式。
 * 系統裡消費紀錄實際來自兩個不同資料表：
 * - Transaction：預約結帳（線上預約 → 到店施作 → 結帳）
 * - WalkInOrder：現場開單（不一定有預約，臨櫃直接開單）
 * 這裡統一成同一種格式，混合排序後顯示，會員信息頁才看得到完整消費全貌。
 */
@Data
@Builder
public class ConsumptionRecordResponse {
    private String sourceLabel; // 「預約結帳」或「現場開單」
    private String sourceType; // "APPOINTMENT" 或 "WALKIN"，供前端點擊查看明細時判斷要打哪支 API
    private Long recordId; // 對應的 appointment id 或 walk-in order id
    private String code; // 顯示用編號，例如 AP001 或 現場單#3
    private String petName;
    private LocalDateTime time; // 付款時間
    private String handledBy;
    private String paymentMethodLabel;
    private int amount;
    private boolean paid;
}
