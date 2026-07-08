package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 寵物毛髮長度（影響服務項目推薦與美容時長估算）
 */
@Getter
public enum CoatType {
    SHORT ("短毛"),
    LONG  ("長毛"),
    MEDIUM("中長毛");

    private final String label;

    CoatType(String label) {
        this.label = label;
    }
}
