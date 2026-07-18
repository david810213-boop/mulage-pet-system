package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 線上轉帳儲值申請狀態
 * PENDING   顧客已送出匯款資訊，等待店家核帳
 * CONFIRMED 店家已核對到帳，餘額已加值
 * REJECTED  店家核帳未通過（金額不符 / 查無此筆匯款等）
 */
@Getter
public enum TopUpStatus {
    PENDING("待核帳"),
    CONFIRMED("已入帳"),
    REJECTED("已駁回");

    private final String label;

    TopUpStatus(String label) {
        this.label = label;
    }
}
