package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 狗狗體重級距（2026-08-24 新增，成犬/幼犬定價流程簡化用）。
 *
 * 邊界值逐字對照 DOG001~036 實際建立時用的體重描述文字（見
 * db/migration/V4__seed_dog_items.sql），不是憑印象重新定義：
 * 小型5kg以下／中小型6-10kg／中型11-16kg／中大型17-22kg／大型23-27kg／
 * 特大型28-33kg。
 *
 * 級距之間刻意留有整數空隙（例如 5→6、10→11），這是原始價目表設計本身就有的
 * 空隙，不是這次新增功能造成的。{@link #forWeight(double)} 用「就近無條件
 * 進位到下一級距」處理落在空隙裡的體重（例如 5.5kg 算進中小型），避免真的
 * 落在門檻中間的狗量出來找不到對應級距。
 */
@Getter
public enum DogWeightTier {
    SMALL("小型犬", 0, 5),
    MEDIUM_SMALL("中小型犬", 6, 10),
    MEDIUM("中型犬", 11, 16),
    MEDIUM_LARGE("中大型犬", 17, 22),
    LARGE("大型犬", 23, 27),
    EXTRA_LARGE("特大型犬", 28, 33);

    private final String label;
    private final double minKg;
    private final double maxKg;

    DogWeightTier(String label, double minKg, double maxKg) {
        this.label = label;
        this.minKg = minKg;
        this.maxKg = maxKg;
    }

    /**
     * 依體重找對應級距。落在空隙或超過最大級距上限的體重，歸進「就近的下一個
     * 級距」（超過 33kg 則歸進最高的 EXTRA_LARGE，這是目前價目表能涵蓋的上限，
     * 更重的狗需要店家另外報價，系統這裡先不處理那種特殊情況）。
     */
    public static DogWeightTier forWeight(double weightKg) {
        for (DogWeightTier tier : values()) {
            if (weightKg <= tier.maxKg) return tier;
        }
        return EXTRA_LARGE;
    }
}
