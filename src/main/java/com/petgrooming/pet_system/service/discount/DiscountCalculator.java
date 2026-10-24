package com.petgrooming.pet_system.service.discount;

import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.service.CatFirstVisitDiscountService;
import com.petgrooming.pet_system.service.CatRewashDiscountService;
import com.petgrooming.pet_system.service.DogFirstVisitDiscountService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 結帳折扣的統一規則：服務促銷與會員儲值折扣「擇優不疊加」，逐項目計算。
 *
 * <ul>
 *   <li>服務促銷依優先順序：狗狗首次體驗 → 貓咪首次體驗 → 貓咪 90 天回洗。
 *       實際上互斥（分屬狗／貓的項目分類；首次體驗代表沒有洗澡紀錄，不可能同時符合回洗）</li>
 *   <li>會員折扣只限儲值金付款，且項目本身可享會員折扣（GroomingItem.discountEligible）</li>
 *   <li>兩者都適用時取價格較低者；同價時以服務促銷計</li>
 * </ul>
 *
 * 背景與取捨見 docs/adr/0004-discount-strategy.md。
 */
@Component
public class DiscountCalculator {

    private final List<SpecialDiscountPolicy> policies;

    public DiscountCalculator(DogFirstVisitDiscountService dogFirstVisit,
                              CatFirstVisitDiscountService catFirstVisit,
                              CatRewashDiscountService catRewash) {
        this.policies = List.of(dogFirstVisit, catFirstVisit, catRewash);
    }

    public EligibleOffers eligibleOffers(Appointment appointment) {
        return new EligibleOffers(policies.stream().filter(p -> p.isEligible(appointment)).toList());
    }

    public EligibleOffers eligibleOffers(WalkInOrder order) {
        return new EligibleOffers(policies.stream().filter(p -> p.isEligible(order)).toList());
    }

    /**
     * 單一項目擇優。
     *
     * @param special                  這個項目適用的服務促銷；沒有則傳 null
     * @param memberDiscountApplicable 項目可享會員折扣，且本次是儲值金付款
     * @param memberDiscountRate       會員折扣率（例如 0.9）；不適用會員折扣時傳 1.0，不影響結果
     */
    public static DiscountResolution resolve(double price, SpecialDiscountPolicy special,
                                             boolean memberDiscountApplicable, double memberDiscountRate) {
        if (special != null && memberDiscountApplicable) {
            double specialPrice = price * special.rate();
            double memberPrice = price * memberDiscountRate;
            return specialPrice <= memberPrice
                    ? new DiscountResolution(specialPrice, special.type())
                    : new DiscountResolution(memberPrice, DiscountType.MEMBER);
        }
        if (special != null) {
            return new DiscountResolution(price * special.rate(), special.type());
        }
        if (memberDiscountApplicable) {
            return new DiscountResolution(price * memberDiscountRate, DiscountType.MEMBER);
        }
        return new DiscountResolution(price, DiscountType.NONE);
    }
}
