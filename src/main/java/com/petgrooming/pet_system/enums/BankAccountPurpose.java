package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 匯款收款帳戶用途——區分「現場/預約結帳」與「LIFF 線上儲值」兩組不同帳戶。
 * 店家反映：儲值金常有大額匯款，跟現場結帳的日常小額收款要分開，方便對帳與資金調度。
 */
@Getter
public enum BankAccountPurpose {
    CHECKOUT("結帳收款（預約結帳／現場開單）"),
    TOPUP("儲值金收款（LIFF 線上儲值，大額專用）");

    private final String label;

    BankAccountPurpose(String label) {
        this.label = label;
    }
}
