package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 消費明細用：這個項目實際套用（或可能套用）的折扣類型。
 * 需求 8-1 修正：90 天回洗優惠與會員儲值折扣只能擇一，不疊加。
 */
@Getter
public enum DiscountType {
    NONE("原價（不打折）"),
    MEMBER("會員折扣"),
    REWASH("回洗優惠"),
    FIRST_VISIT("首次體驗優惠"); // 需求（追加）：狗狗首次消費 9 折

    private final String label;

    DiscountType(String label) {
        this.label = label;
    }
}
