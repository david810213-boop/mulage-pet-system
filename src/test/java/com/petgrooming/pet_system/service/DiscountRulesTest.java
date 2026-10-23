package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

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
        var result = dogFirstVisit.resolvePreferredDiscount(1000, true, true, MemberCardTier.NORMAL.getDiscount());

        assertThat(result.price()).isCloseTo(900, within(0.001));
    }

    @Test
    void memberDiscountWinsWhenLarger() {
        var result = dogFirstVisit.resolvePreferredDiscount(1000, true, true, MemberCardTier.VIP.getDiscount());

        assertThat(result.price()).isCloseTo(850, within(0.001));
    }

    @Test
    void discountsAreNeverStacked() {
        double vip = MemberCardTier.VIP.getDiscount();

        assertThat(dogFirstVisit.resolvePreferredDiscount(1000, true, true, vip).price()).isNotCloseTo(1000 * 0.9 * vip, within(0.001));
        assertThat(catFirstVisit.resolvePreferredDiscount(1000, true, true, vip).price()).isNotCloseTo(1000 * 0.9 * vip, within(0.001));
        assertThat(catRewash.resolvePreferredDiscount(1000, true, true, vip).price()).isNotCloseTo(1000 * 0.9 * vip, within(0.001));
    }

    @Test
    void itemsNotEligibleForMemberDiscountKeepFullPrice() {
        var result = dogFirstVisit.resolvePreferredDiscount(1000, false, false, MemberCardTier.VIP.getDiscount());

        assertThat(result.price()).isCloseTo(1000, within(0.001));
    }

    @Test
    void catRewashAppliesTenPercentOffWhenNoMemberDiscount() {
        var result = catRewash.resolvePreferredDiscount(1000, true, false, 1.0);

        assertThat(result.price()).isCloseTo(900, within(0.001));
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
