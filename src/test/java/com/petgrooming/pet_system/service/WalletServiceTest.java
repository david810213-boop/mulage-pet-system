package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.exception.WalletException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest extends IntegrationTestBase {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestData testData;

    @Test
    void depositBelowMinimumIsRejected() {
        User customer = testData.customer();

        assertThatThrownBy(() -> walletService.deposit(customer.getUsername(), deposit(4999)))
                .isInstanceOf(WalletException.class);
        assertThat(balanceOf(customer)).isZero();
    }

    @Test
    void villagePlanDepositAddsBonusAndHasNoExpiry() {
        User customer = testData.customer();

        walletService.deposit(customer.getUsername(), deposit(5000));

        Wallet wallet = walletOf(customer);
        assertThat(wallet.getBalance()).isEqualTo(5200);
        assertThat(wallet.getCardTier()).isEqualTo(MemberCardTier.VILLAGE);
        assertThat(wallet.getCardExpiresAt()).isNull();
    }

    @Test
    void cardTierExpiresOneYearMinusOneDayAfterActivation() {
        User customer = testData.customer();

        walletService.deposit(customer.getUsername(), deposit(15000));

        Wallet wallet = walletOf(customer);
        assertThat(wallet.getCardTier()).isEqualTo(MemberCardTier.GOLD);
        assertThat(wallet.getCardExpiresAt()).isEqualTo(LocalDate.now().plusYears(1).minusDays(1));
    }

    @Test
    void smallerDepositWithinValidPeriodKeepsHigherTier() {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(15000));
        LocalDate expiry = walletOf(customer).getCardExpiresAt();

        walletService.deposit(customer.getUsername(), deposit(8000));

        Wallet wallet = walletOf(customer);
        assertThat(wallet.getCardTier()).isEqualTo(MemberCardTier.GOLD);
        assertThat(wallet.getCardExpiresAt()).isEqualTo(expiry);
        assertThat(wallet.getBalance()).isEqualTo(23000);
    }

    @Test
    void deductMoreThanBalanceIsRejected() {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(8000));

        assertThatThrownBy(() -> walletService.deduct(customer.getUsername(), 8001, null))
                .isInstanceOf(WalletException.class);
        assertThat(balanceOf(customer)).isEqualTo(8000);
    }

    @Test
    void concurrentDeductionsNeverOverdraw() throws Exception {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(8000));

        Concurrently.Result result = Concurrently.run(10,
                () -> walletService.deduct(customer.getUsername(), 1000, null));

        assertThat(result.succeeded()).isEqualTo(8);
        assertThat(result.failures()).allMatch(WalletException.class::isInstance);
        assertThat(balanceOf(customer)).isZero();
    }

    @Test
    void concurrentDepositsAndDeductionsLoseNoUpdate() throws Exception {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(30000));
        AtomicInteger turn = new AtomicInteger();

        Concurrently.Result result = Concurrently.run(10, () -> {
            if (turn.getAndIncrement() % 2 == 0) {
                walletService.deposit(customer.getUsername(), deposit(5000));
            } else {
                walletService.deduct(customer.getUsername(), 1000, null);
            }
        });

        assertThat(result.failures()).isEmpty();
        // 5 筆儲值各 5000 ＋ 村民方案贈點 200，5 筆扣款各 1000
        assertThat(balanceOf(customer)).isEqualTo(30000 + 5 * 5200 - 5 * 1000);
    }

    private static DepositRequest deposit(int amount) {
        DepositRequest req = new DepositRequest();
        req.setAmount(amount);
        return req;
    }

    private Wallet walletOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow();
    }

    private int balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).map(Wallet::getBalance).orElse(0);
    }
}
