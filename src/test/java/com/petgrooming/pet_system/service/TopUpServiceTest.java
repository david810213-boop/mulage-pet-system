package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CreateTopUpRequest;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.exception.WalletException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 顧客轉帳儲值申請：送出、店家確認入帳、駁回，以及重複處理的防護。
 */
class TopUpServiceTest extends IntegrationTestBase {

    @Autowired
    private TopUpService topUpService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestData testData;

    @Test
    void requestBelowMinimumIsRejected() {
        User customer = testData.customer();

        assertThatThrownBy(() -> topUpService.submit(customer.getUsername(), request(4000)))
                .isInstanceOf(WalletException.class);
    }

    @Test
    void confirmingCreditsTheWallet() {
        User customer = testData.customer();
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), request(8000));

        TopUpRequestResponse confirmed = topUpService.confirm(submitted.getId(), "店長");

        assertThat(confirmed.getStatus()).isEqualTo(TopUpStatus.CONFIRMED);
        assertThat(balanceOf(customer)).isEqualTo(8000);
    }

    @Test
    void requestCannotBeConfirmedTwice() {
        User customer = testData.customer();
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), request(8000));
        topUpService.confirm(submitted.getId(), "店長");

        assertThatThrownBy(() -> topUpService.confirm(submitted.getId(), "店長"))
                .isInstanceOf(WalletException.class);
        assertThat(balanceOf(customer)).isEqualTo(8000);
    }

    @Test
    void rejectedRequestCannotBeConfirmed() {
        User customer = testData.customer();
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), request(8000));
        topUpService.reject(submitted.getId(), "店長", "查無此筆匯款");

        assertThatThrownBy(() -> topUpService.confirm(submitted.getId(), "店長"))
                .isInstanceOf(WalletException.class);
        assertThat(balanceOf(customer)).isZero();
    }

    /** 兩位員工同時按下「確認入帳」（或同一人連點），只能入帳一次。 */
    @Test
    void concurrentConfirmationsCreditOnlyOnce() throws Exception {
        User customer = testData.customer();
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), request(8000));

        Concurrently.Result result = Concurrently.run(5, () -> topUpService.confirm(submitted.getId(), "店長"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(8000);
    }

    private static CreateTopUpRequest request(int amount) {
        CreateTopUpRequest req = new CreateTopUpRequest();
        req.setAmount(amount);
        req.setLastFiveDigits("12345");
        return req;
    }

    private int balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).map(Wallet::getBalance).orElse(0);
    }
}
