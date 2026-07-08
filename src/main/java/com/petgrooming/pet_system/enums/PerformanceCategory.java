package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 員工績效大項分類
 * 對應積分表的七大類
 */
@Getter
public enum PerformanceCategory {
    BATH_SMALL  ("洗（小型）",  10.0),
    BATH_LARGE  ("洗（大型）",  20.0),
    BATH_CAT_S  ("洗（小貓）",  20.0),
    BATH_CAT_L  ("洗（大貓）",  30.0),
    BLOW_SMALL  ("吹（小型）",  10.0),
    BLOW_LARGE  ("吹（大型）",  20.0),
    BLOW_CAT_S  ("吹（小貓）",  20.0),
    BLOW_CAT_L  ("吹（大貓）",  30.0),
    BASIC       ("基礎美容",    10.0),
    TRIM        ("剪毛",        20.0),
    AD          ("特殊（AD）",  10.0),
    HC          ("特殊（HC）",  15.0),
    PARTIAL     ("局部修剪",     5.0),
    SPECIAL     ("特殊項目",     5.0),
    CHECKIN     ("接進",         5.0),
    CHECKOUT    ("接出",         5.0),
    COMPLETE    ("完成",         5.0),
    OTHER       ("其他",         0.0); // GS001~GS012 等加值項目不計分

    private final String label;
    private final double defaultPoints; // 預設積分（可在後台調整）

    PerformanceCategory(String label, double defaultPoints) {
        this.label = label;
        this.defaultPoints = defaultPoints;
    }
}
