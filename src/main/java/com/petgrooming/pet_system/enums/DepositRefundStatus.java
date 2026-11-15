package com.petgrooming.pet_system.enums;

import lombok.Getter;

/** 訂金退款進度：系統算出應退金額後為待退款，店員匯款給顧客後標記已退款。 */
@Getter
public enum DepositRefundStatus {
    NONE("無"),
    PENDING("待退款"),
    DONE("已退款");

    private final String label;

    DepositRefundStatus(String label) {
        this.label = label;
    }
}
