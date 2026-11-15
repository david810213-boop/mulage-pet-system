package com.petgrooming.pet_system.enums;

import lombok.Getter;

/** 顧客回報的訂金匯款：店家核對銀行入帳後確認或駁回。 */
@Getter
public enum DepositPaymentStatus {
    PENDING("待核帳"),
    CONFIRMED("已入帳"),
    REJECTED("已駁回");

    private final String label;

    DepositPaymentStatus(String label) {
        this.label = label;
    }
}
