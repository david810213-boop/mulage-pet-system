package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 寵物體型分類（影響浴吹積分與服務項目自動選擇）
 * 狗：體重 ≤ 15kg → 小型；> 15kg → 大型
 * 貓：體重 ≤ 5kg  → 小貓；> 5kg  → 大貓
 */
@Getter
public enum PetSizeCategory {
    SMALL_DOG ("小型犬", PerformanceCategory.BATH_SMALL, PerformanceCategory.BLOW_SMALL, "BATH_S", "BLOW_S"),
    LARGE_DOG ("大型犬", PerformanceCategory.BATH_LARGE, PerformanceCategory.BLOW_LARGE, "BATH_L", "BLOW_L"),
    SMALL_CAT ("小貓",   PerformanceCategory.BATH_CAT_S, PerformanceCategory.BLOW_CAT_S, "BATH_CS", "BLOW_CS"),
    LARGE_CAT ("大貓",   PerformanceCategory.BATH_CAT_L, PerformanceCategory.BLOW_CAT_L, "BATH_CL", "BLOW_CL"),
    OTHER     ("其他",   null, null, null, null);

    private final String label;
    private final PerformanceCategory bathCategory; // 對應的洗澡績效大項
    private final PerformanceCategory blowCategory; // 對應的吹毛績效大項
    private final String bathItemCode;              // 對應的洗澡 itemCode
    private final String blowItemCode;              // 對應的吹毛 itemCode

    PetSizeCategory(String label, PerformanceCategory bathCategory,
                    PerformanceCategory blowCategory,
                    String bathItemCode, String blowItemCode) {
        this.label = label;
        this.bathCategory = bathCategory;
        this.blowCategory = blowCategory;
        this.bathItemCode = bathItemCode;
        this.blowItemCode = blowItemCode;
    }

    /**
     * 依寵物類型與體重自動判斷體型分類
     */
    public static PetSizeCategory determine(PetType petType, double weight) {
        if (petType == PetType.DOG) {
            return weight <= 16.0 ? SMALL_DOG : LARGE_DOG;
        } else if (petType == PetType.CAT) {
            return weight <= 5.9 ? SMALL_CAT : LARGE_CAT;
        }
        return OTHER;
    }
}
