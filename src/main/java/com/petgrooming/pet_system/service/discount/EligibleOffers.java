package com.petgrooming.pet_system.service.discount;

import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;

import java.util.List;
import java.util.Optional;

/**
 * 一筆單有資格的服務促銷（依優先順序）。每個項目最多套用一個促銷：取第一個適用這個項目分類的。
 */
public record EligibleOffers(List<SpecialDiscountPolicy> policies) {

    public static final EligibleOffers NONE = new EligibleOffers(List.of());

    public boolean isEmpty() {
        return policies.isEmpty();
    }

    /** 這個分類的項目可套用的促銷；沒有則為空。 */
    public Optional<SpecialDiscountPolicy> offerFor(PerformanceCategory category) {
        return policies.stream().filter(p -> p.appliesTo(category)).findFirst();
    }

    /** 這個分類的項目是否符合某一種促銷（例如明細標示「符合首次體驗優惠」，狗貓不分）。 */
    public boolean applies(DiscountType type, PerformanceCategory category) {
        return policies.stream().anyMatch(p -> p.type() == type && p.appliesTo(category));
    }

    /** 這個項目在促銷與會員折扣之間擇優。 */
    public DiscountResolution resolve(double price, PerformanceCategory category,
                                      boolean memberDiscountApplicable, double memberDiscountRate) {
        return DiscountCalculator.resolve(price, offerFor(category).orElse(null),
                memberDiscountApplicable, memberDiscountRate);
    }
}
