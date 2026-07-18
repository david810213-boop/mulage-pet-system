package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 會員得知本店的來源管道（供店家分析新客開發成效用）
 */
@Getter
public enum CustomerSource {
    FRIEND_REFERRAL ("親友介紹"),
    SOCIAL_MEDIA    ("社群媒體／廣告"),
    SEARCH_ENGINE   ("Google／網路搜尋"),
    WALK_IN         ("路過／店面招牌"),
    REPEAT_CUSTOMER ("舊客戶回訪"),
    OTHER           ("其他");

    private final String label;

    CustomerSource(String label) {
        this.label = label;
    }
}
