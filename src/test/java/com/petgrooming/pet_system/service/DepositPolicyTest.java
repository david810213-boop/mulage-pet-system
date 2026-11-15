package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.enums.PetType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 店家公告《預約須知｜訂金及取消規範》的計算規則（純計算，不需要資料庫）。
 */
class DepositPolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2099, 1, 10, 12, 0);

    @Test
    void singleSmallPetNeedsNoDeposit() {
        assertThat(DepositPolicy.shares(List.of(false), false)).containsExactly(0);
    }

    @Test
    void multiplePetsPayFiveHundredEach() {
        assertThat(DepositPolicy.shares(List.of(false, false), false)).containsExactly(500, 500);
        assertThat(DepositPolicy.shares(List.of(false, false, false, false, false), false))
                .containsExactly(500, 500, 500, 500, 500);
    }

    @Test
    void firstVisitLargeDogPaysOneThousandEvenAlone() {
        assertThat(DepositPolicy.shares(List.of(true), false)).containsExactly(1000);
    }

    @Test
    void firstVisitLargeDogWithCatIsCountedSeparately() {
        assertThat(DepositPolicy.shares(List.of(true, false), false)).containsExactly(1000, 500);
    }

    @Test
    void exemptDayPaysNothing() {
        assertThat(DepositPolicy.shares(List.of(true, false, false), true)).containsExactly(0, 0, 0);
    }

    @Test
    void largeDogStartsAtTwentyThreeKilograms() {
        assertThat(DepositPolicy.isLargeDog(PetType.DOG, 23.0)).isTrue();
        assertThat(DepositPolicy.isLargeDog(PetType.DOG, 22.9)).isFalse();
        assertThat(DepositPolicy.isLargeDog(PetType.DOG, null)).isFalse();
        assertThat(DepositPolicy.isLargeDog(PetType.CAT, 30.0)).isFalse();
    }

    @Test
    void refundTiersFollowTheNotice() {
        assertThat(DepositPolicy.refundOnCancel(1000, START, START.minusHours(72), false))
                .isEqualTo(new DepositPolicy.Refund(1000, 0));
        assertThat(DepositPolicy.refundOnCancel(1000, START, START.minusHours(72).plusMinutes(1), false))
                .isEqualTo(new DepositPolicy.Refund(500, 500));
        assertThat(DepositPolicy.refundOnCancel(1000, START, START.minusHours(24), false))
                .isEqualTo(new DepositPolicy.Refund(500, 500));
        assertThat(DepositPolicy.refundOnCancel(1000, START, START.minusHours(24).plusMinutes(1), false))
                .isEqualTo(new DepositPolicy.Refund(0, 1000));
        assertThat(DepositPolicy.refundOnCancel(1000, START, START.plusHours(1), false))
                .isEqualTo(new DepositPolicy.Refund(0, 1000));
    }

    @Test
    void storeCauseAlwaysRefundsInFull() {
        assertThat(DepositPolicy.refundOnCancel(500, START, START.minusHours(1), true))
                .isEqualTo(new DepositPolicy.Refund(500, 0));
    }

    @Test
    void rescheduleAllowedOnceAtLeastSeventyTwoHoursAhead() {
        assertThat(DepositPolicy.canRescheduleWithDeposit(START, START.minusHours(72), 0)).isTrue();
        assertThat(DepositPolicy.canRescheduleWithDeposit(START, START.minusHours(71), 0)).isFalse();
        assertThat(DepositPolicy.canRescheduleWithDeposit(START, START.minusDays(10), 1)).isFalse();
    }

    @Test
    void paymentDueIsTwentyFourHoursButNotAfterStart() {
        LocalDateTime now = START.minusDays(5);
        assertThat(DepositPolicy.paymentDueAt(now, START)).isEqualTo(now.plusHours(24));
        assertThat(DepositPolicy.paymentDueAt(START.minusHours(3), START)).isEqualTo(START);
    }
}
