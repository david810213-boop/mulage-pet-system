package com.petgrooming.pet_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 需求（追加，2026-09-04）：LIFF 預約頁顯示「這隻寵物目前的折扣資格狀態」，
 * 讓顧客自己看得到距上次洗澡幾天、優惠期內還是已過期，以及服務項目價格
 * 能不能顯示「原價劃掉+折扣價」。
 *
 * 三種折扣（首次體驗優惠／90 天回洗優惠／會員儲值折扣）互斥擇優，這裡把
 * 「特殊優惠」（首次體驗/回洗，兩者本身也互斥）跟「會員儲值折扣」分開回傳，
 * 前端渲染時兩者取較優惠者，邏輯跟後端 resolvePreferredDiscount() 一致。
 */
@Data
@AllArgsConstructor
public class PetDiscountStatusResponse {

    private String petType; // DOG / CAT

    private boolean firstVisitEligible; // 首次體驗優惠資格（貓狗皆可能）
    private boolean rewashEligible;     // 90 天回洗優惠資格（僅貓咪）

    private Long lastBathDaysAgo; // 距上次洗澡幾天，從未洗過（查無紀錄）則為 null

    private Double memberDiscountRate; // 會員自己的儲值折扣率（1.0 = 沒有折扣）

    private String specialDiscountLabel;    // "首次體驗優惠" / "回洗優惠" / null（都不符合）
    private Double specialDiscountRate;     // 0.9，都不符合則為 null
    private List<String> specialDiscountCategories; // 這個特殊優惠適用的積分分類名稱清單
}
