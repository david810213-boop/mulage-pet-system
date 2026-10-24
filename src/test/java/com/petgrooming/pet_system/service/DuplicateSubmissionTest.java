package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.CreateTopUpRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.exception.DuplicateRequestException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.TopUpRequestRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金流操作的重複送出防護（連點、網路重送、兩位員工同時操作）：同一筆只會入帳／扣款／退款一次。
 * 各測試的會員都先有錢包，避免「同時建立錢包」的唯一鍵衝突掩蓋了真正要驗證的鎖。
 */
class DuplicateSubmissionTest extends IntegrationTestBase {

    @Autowired private WalletService walletService;
    @Autowired private TopUpService topUpService;
    @Autowired private PaymentService paymentService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private WalkInPaymentService walkInPaymentService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TopUpRequestRepository topUpRequestRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private TestData testData;

    @Test
    void concurrentTopUpConfirmationsCreditOnceWhenWalletExists() throws Exception {
        User customer = customerWithBalance(5000);
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), topUp(8000, null));

        Concurrently.Result result = Concurrently.run(5, () -> topUpService.confirm(submitted.getId(), "店長"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(5000 + 200 + 8000);
    }

    @Test
    void confirmAndRejectAtTheSameTimeOnlyOneWins() throws Exception {
        User customer = customerWithBalance(5000);
        TopUpRequestResponse submitted = topUpService.submit(customer.getUsername(), topUp(8000, null));
        int before = balanceOf(customer);

        Concurrently.Result result = Concurrently.run(6, () -> {
            if (Thread.currentThread().getId() % 2 == 0) {
                topUpService.confirm(submitted.getId(), "店長");
            } else {
                topUpService.reject(submitted.getId(), "店長", "金額不符");
            }
        });

        assertThat(result.succeeded()).isEqualTo(1);
        TopUpStatus status = topUpRequestRepository.findById(submitted.getId()).orElseThrow().getStatus();
        assertThat(balanceOf(customer))
                .isEqualTo(status == TopUpStatus.CONFIRMED ? before + 8000 : before);
    }

    @Test
    void sameKeyCreatesOnlyOneTopUpRequest() throws Exception {
        User customer = testData.customer();
        String key = UUID.randomUUID().toString();

        Concurrently.Result result = Concurrently.run(5,
                () -> topUpService.submit(customer.getUsername(), topUp(8000, key)));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failures()).allMatch(DuplicateRequestException.class::isInstance);
        assertThat(topUpRequestRepository.findByUserId(customer.getId())).hasSize(1);
    }

    @Test
    void requestsWithoutKeyAreNotDeduplicated() {
        User customer = testData.customer();
        topUpService.submit(customer.getUsername(), topUp(8000, null));
        topUpService.submit(customer.getUsername(), topUp(8000, null));

        assertThat(topUpRequestRepository.findByUserId(customer.getId())).hasSize(2);
    }

    @Test
    void sameKeyDepositsOnlyOnce() throws Exception {
        User customer = customerWithBalance(5000);
        String key = UUID.randomUUID().toString();

        Concurrently.Result result = Concurrently.run(5,
                () -> walletService.deposit(customer.getUsername(), deposit(15000, key)));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(5000 + 200 + 15000);
        assertThatThrownBy(() -> walletService.deposit(customer.getUsername(), deposit(15000, key)))
                .isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    void failedDepositDoesNotUseUpTheKey() {
        User customer = customerWithBalance(5000);
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> walletService.deposit(customer.getUsername(), deposit(100, key)))
                .hasMessage("單筆儲值最低 $5,000");
        walletService.deposit(customer.getUsername(), deposit(15000, key));

        assertThat(balanceOf(customer)).isEqualTo(5000 + 200 + 15000);
    }

    @Test
    void concurrentAppointmentRefundsRefundOnce() throws Exception {
        User staff = testData.staff();
        User customer = customerWithBalance(15000);
        Appointment appointment = readyAppointment(customer, item(1000));
        paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername());
        int afterCheckout = balanceOf(customer);

        Concurrently.Result result = Concurrently.run(5,
                () -> paymentService.refund(appointment.getId(), staff.getUsername()));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(15000);
        assertThat(afterCheckout).isLessThan(15000);
    }

    @Test
    void concurrentWalkInWalletCheckoutsChargeOnce() throws Exception {
        User staff = testData.staff();
        User customer = customerWithBalance(15000);
        WalkInOrderResponse order = readyWalkInOrder(customer, staff, item(1000));

        Concurrently.Result result = Concurrently.run(5,
                () -> walkInPaymentService.checkout(order.getId(), PaymentMethod.WALLET, staff.getUsername()));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(walletService.getTransactions(customer.getUsername()))
                .filteredOn(tx -> tx.getType().name().equals("DEDUCT"))
                .hasSize(1);
    }

    @Test
    void concurrentWalkInRefundsRefundOnce() throws Exception {
        User staff = testData.staff();
        User customer = customerWithBalance(15000);
        WalkInOrderResponse order = readyWalkInOrder(customer, staff, item(1000));
        walkInPaymentService.checkout(order.getId(), PaymentMethod.WALLET, staff.getUsername());

        Concurrently.Result result = Concurrently.run(5,
                () -> walkInPaymentService.refund(order.getId(), staff.getUsername()));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(15000);
    }

    private User customerWithBalance(int amount) {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(amount, null));
        return customer;
    }

    private Appointment readyAppointment(User customer, GroomingItem item) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName("毛孩" + UUID.randomUUID().toString().substring(0, 6))
                .petType("CAT")
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .selectedItems(List.of(item))
                .totalAmount(item.getPrice().intValue())
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .finalCheckDone(true)
                .build());
    }

    private WalkInOrderResponse readyWalkInOrder(User customer, User staff, GroomingItem item) {
        WalkInOrderCreateRequest.Item line = new WalkInOrderCreateRequest.Item();
        line.setItemCode(item.getItemCode());
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(customer.getUsername());
        req.setPetName("小白");
        req.setItems(new ArrayList<>(List.of(line)));
        req.setRetailItems(new ArrayList<>());
        WalkInOrderResponse order = walkInOrderService.create(req, staff.getUsername());
        walkInOrderRepository.findById(order.getId()).ifPresent(o -> {
            o.setFinalCheckDone(true);
            walkInOrderRepository.save(o);
        });
        return order;
    }

    private GroomingItem item(int price) {
        String code = "D" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code)
                .name("重複送出測試" + code)
                .price((double) price)
                .points(1.0)
                .performanceCategory(PerformanceCategory.OTHER)
                .build());
    }

    private static CreateTopUpRequest topUp(int amount, String key) {
        CreateTopUpRequest req = new CreateTopUpRequest();
        req.setAmount(amount);
        req.setLastFiveDigits("12345");
        req.setIdempotencyKey(key);
        return req;
    }

    private static DepositRequest deposit(int amount, String key) {
        DepositRequest req = new DepositRequest();
        req.setAmount(amount);
        req.setIdempotencyKey(key);
        return req;
    }

    private static CheckoutRequest pay(PaymentMethod method) {
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(method);
        return req;
    }

    private int balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).map(Wallet::getBalance).orElse(0);
    }
}
