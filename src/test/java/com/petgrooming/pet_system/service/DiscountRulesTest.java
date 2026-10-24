package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.service.discount.DiscountCalculator;
import com.petgrooming.pet_system.service.discount.EligibleOffers;
import com.petgrooming.pet_system.service.discount.SpecialDiscountPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 折扣與會員等級的純計算規則（不需資料庫）。
 * 店家規則：會員折扣與首次體驗／貓咪回洗優惠「擇優適用、不疊加」。
 */
class DiscountRulesTest {

    private final DogFirstVisitDiscountService dogFirstVisit = new DogFirstVisitDiscountService(null, null);
    private final CatFirstVisitDiscountService catFirstVisit = new CatFirstVisitDiscountService(null, null);
    private final CatRewashDiscountService catRewash = new CatRewashDiscountService(null, null, null);

    @Test
    void firstVisitWinsWhenMemberDiscountIsSmaller() {
        var result = DiscountCalculator.resolve(1000, dogFirstVisit, true, MemberCardTier.NORMAL.getDiscount());

        assertThat(result.price()).isCloseTo(900, within(0.001));
        assertThat(result.type()).isEqualTo(DiscountType.FIRST_VISIT);
    }

    @Test
    void memberDiscountWinsWhenLarger() {
        var result = DiscountCalculator.resolve(1000, dogFirstVisit, true, MemberCardTier.VIP.getDiscount());

        assertThat(result.price()).isCloseTo(850, within(0.001));
        assertThat(result.type()).isEqualTo(DiscountType.MEMBER);
    }

    @Test
    void specialOfferWinsATie() {
        var result = DiscountCalculator.resolve(1000, catRewash, true, MemberCardTier.GOLD.getDiscount());

        assertThat(result.type()).isEqualTo(DiscountType.REWASH);
        assertThat(result.price()).isCloseTo(900, within(0.001));
    }

    @Test
    void discountsAreNeverStacked() {
        double vip = MemberCardTier.VIP.getDiscount();

        for (SpecialDiscountPolicy policy : List.of(dogFirstVisit, catFirstVisit, catRewash)) {
            assertThat(DiscountCalculator.resolve(1000, policy, true, vip).price())
                    .isNotCloseTo(1000 * 0.9 * vip, within(0.001));
        }
    }

    @Test
    void itemsNotEligibleForAnyDiscountKeepFullPrice() {
        var result = DiscountCalculator.resolve(1000, null, false, MemberCardTier.VIP.getDiscount());

        assertThat(result.price()).isCloseTo(1000, within(0.001));
        assertThat(result.type()).isEqualTo(DiscountType.NONE);
    }

    @Test
    void catRewashAppliesTenPercentOffWhenNoMemberDiscount() {
        var result = DiscountCalculator.resolve(1000, catRewash, false, 1.0);

        assertThat(result.price()).isCloseTo(900, within(0.001));
    }

    @Test
    void eachOfferAppliesOnlyToItsCategories() {
        var offers = new EligibleOffers(List.of(dogFirstVisit, catFirstVisit, catRewash));

        assertThat(offers.offerFor(PerformanceCategory.BATH_SMALL)).contains(dogFirstVisit);
        assertThat(offers.offerFor(PerformanceCategory.BATH_LARGE)).contains(dogFirstVisit);
        assertThat(offers.offerFor(PerformanceCategory.BATH_CAT_S)).contains(catFirstVisit); // 首次體驗優先於回洗
        assertThat(offers.offerFor(PerformanceCategory.TRIM)).isEmpty();
        assertThat(offers.applies(DiscountType.FIRST_VISIT, PerformanceCategory.BATH_CAT_L)).isTrue();
        assertThat(offers.applies(DiscountType.REWASH, PerformanceCategory.BATH_SMALL)).isFalse();
        assertThat(new EligibleOffers(List.of(catRewash)).offerFor(PerformanceCategory.BATH_CAT_L)).contains(catRewash);
        assertThat(EligibleOffers.NONE.resolve(1000, PerformanceCategory.BATH_SMALL, false, 1.0).price())
                .isCloseTo(1000, within(0.001));
    }

    @ParameterizedTest(name = "單筆儲值 {0} → {1}")
    @CsvSource({
            "0, NONE", "4999, NONE",
            "5000, VILLAGE", "7999, VILLAGE",
            "8000, NORMAL", "14999, NORMAL",
            "15000, GOLD", "29999, GOLD",
            "30000, DIAMOND", "49999, DIAMOND",
            "50000, VIP", "100000, VIP"
    })
    void cardTierIsDecidedBySingleDepositAmount(int amount, MemberCardTier expected) {
        assertThat(MemberCardTier.fromAmount(amount)).isEqualTo(expected);
    }

    @Test
    void cardExpiresOneYearMinusOneDayAfterStart() {
        assertThat(WalletService.cardExpiryFrom(LocalDate.of(2026, 10, 24))).isEqualTo(LocalDate.of(2027, 10, 23));
        assertThat(WalletService.cardExpiryFrom(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void onlyCreditCardAddsHandlingFee() {
        assertThat(PaymentMethod.CASH.calculateFinalAmount(1000)).isEqualTo(1000);
        assertThat(PaymentMethod.LINE_PAY.calculateFinalAmount(1000)).isEqualTo(1000);
        assertThat(PaymentMethod.WALLET.calculateFinalAmount(1000)).isEqualTo(1000);
        assertThat(PaymentMethod.CREDIT_CARD.calculateFinalAmount(1000)).isEqualTo(1020);
    }
}
