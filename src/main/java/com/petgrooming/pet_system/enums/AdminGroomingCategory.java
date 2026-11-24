package com.petgrooming.pet_system.enums;

import com.petgrooming.pet_system.dto.GroomingItemResponse;

/** 管理後台「美容服務項目價目管理」的分類按鈕，只影響畫面分組，不影響預約篩選。 */
public enum AdminGroomingCategory {
    DOG_BATH("狗狗精緻洗"),
    DOG_CARE("狗狗定制調理"),
    DOG_CUT("狗狗大美容"),
    CAT("貓咪"),
    SINGLE("單項加購"),
    SPECIAL("特殊項目");

    private final String label;

    AdminGroomingCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AdminGroomingCategory of(GroomingItemResponse item) {
        String perf = item.getPerformanceCategory();
        if (item.isTierAddon()) {
            return "TRIM".equals(perf) ? DOG_CUT : DOG_CARE;
        }
        if ("SPECIAL".equals(perf) || "SPECIAL2".equals(perf)) {
            return SPECIAL;
        }
        if ("CAT".equals(item.getApplicablePetType())) {
            return CAT;
        }
        if ("DOG".equals(item.getApplicablePetType()) && item.getDogWeightTier() != null) {
            return DOG_BATH;
        }
        return SINGLE;
    }

    public boolean isDog() {
        return this == DOG_BATH || this == DOG_CARE || this == DOG_CUT;
    }
}
