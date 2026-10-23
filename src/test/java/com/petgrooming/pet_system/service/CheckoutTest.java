package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.TransactionResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.exception.WalletException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 結帳流程：付款方式、會員折扣與首次體驗擇優、儲值金扣款、重複結帳與前置條件。
 */
class CheckoutTest extends IntegrationTestBase {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private GroomingItemRepository groomingItemRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestData testData;

    @Test
    void cashCheckoutCompletesTheAppointment() {
        User staff = testData.staff();
        Appointment appointment = readyForCheckout(testData.customer(), "CAT", item(1000, PerformanceCategory.OTHER));

        TransactionResponse tx = paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername());

        assertThat(tx.getFinalAmount()).isEqualTo(1000);
        assertThat(tx.isPaid()).isTrue();
        Appointment saved = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(saved.isPaid()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void appointmentCannotBeCheckedOutTwice() {
        User staff = testData.staff();
        Appointment appointment = readyForCheckout(testData.customer(), "CAT", item(1000, PerformanceCategory.OTHER));
        paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername());

        assertThatThrownBy(() -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername()))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void checkoutRequiresFinalCheck() {
        User staff = testData.staff();
        Appointment appointment = readyForCheckout(testData.customer(), "CAT", item(1000, PerformanceCategory.OTHER));
        appointment.setFinalCheckDone(false);
        appointmentRepository.save(appointment);

        assertThatThrownBy(() -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("核對");
    }

    @Test
    void customerCannotCheckOutAnotherCustomersAppointment() {
        Appointment appointment = readyForCheckout(testData.customer(), "CAT", item(1000, PerformanceCategory.OTHER));
        User stranger = testData.customer();

        assertThatThrownBy(() -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), stranger.getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("權限不足");
    }

    @Test
    void walletCheckoutAppliesMemberDiscountAndDeductsBalance() {
        User staff = testData.staff();
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(15000)); // 金卡 9 折
        Appointment appointment = readyForCheckout(customer, "CAT", item(1000, PerformanceCategory.OTHER));

        TransactionResponse tx = paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername());

        assertThat(tx.getFinalAmount()).isEqualTo(900);
        assertThat(balanceOf(customer)).isEqualTo(15000 - 900);
    }

    @Test
    void walletCheckoutIsStaffOnly() {
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(15000));
        Appointment appointment = readyForCheckout(customer, "CAT", item(1000, PerformanceCategory.OTHER));

        assertThatThrownBy(() -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), customer.getUsername()))
                .isInstanceOf(PaymentException.class);
        assertThat(balanceOf(customer)).isEqualTo(15000);
    }

    @Test
    void insufficientWalletBalanceLeavesAppointmentUnpaid() {
        User staff = testData.staff();
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(5000)); // 餘額 5200，村民方案無折扣
        Appointment appointment = readyForCheckout(customer, "CAT", item(6000, PerformanceCategory.OTHER));

        assertThatThrownBy(() -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername()))
                .isInstanceOf(WalletException.class);
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().isPaid()).isFalse();
        assertThat(balanceOf(customer)).isEqualTo(5200);
    }

    @Test
    void dogFirstVisitPackageGetsTenPercentOffForCash() {
        User staff = testData.staff();
        Appointment appointment = readyForCheckout(testData.customer(), "DOG", item(1000, PerformanceCategory.BATH_SMALL));

        TransactionResponse tx = paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername());

        assertThat(tx.getFinalAmount()).isEqualTo(900);
    }

    @Test
    void dogFirstVisitAndVipDiscountAreNotStacked() {
        User staff = testData.staff();
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(50000)); // VIP 85 折
        Appointment appointment = readyForCheckout(customer, "DOG", item(1000, PerformanceCategory.BATH_SMALL));

        TransactionResponse tx = paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername());

        assertThat(tx.getFinalAmount()).isEqualTo(850);
    }

    @Test
    void wireTransferWaitsForStaffConfirmation() {
        User staff = testData.staff();
        Appointment appointment = readyForCheckout(testData.customer(), "CAT", item(1000, PerformanceCategory.OTHER));

        TransactionResponse tx = paymentService.checkout(appointment.getId(), pay(PaymentMethod.WIRE_TRANSFER), staff.getUsername());

        assertThat(tx.isPaid()).isFalse();
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().isPaid()).isFalse();

        paymentService.confirmWireTransferPayment(appointment.getId(), staff.getUsername());

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().isPaid()).isTrue();
        assertThatThrownBy(() -> paymentService.confirmWireTransferPayment(appointment.getId(), staff.getUsername()))
                .isInstanceOf(PaymentException.class);
    }

    /** 結帳按鈕連點、或兩台裝置同時結帳同一筆預約：只能扣款一次。 */
    @Test
    void concurrentWalletCheckoutsChargeOnlyOnce() throws Exception {
        User staff = testData.staff();
        User customer = testData.customer();
        walletService.deposit(customer.getUsername(), deposit(15000));
        Appointment appointment = readyForCheckout(customer, "CAT", item(1000, PerformanceCategory.OTHER));

        Concurrently.Result result = Concurrently.run(5,
                () -> paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername()));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(balanceOf(customer)).isEqualTo(15000 - 900);
    }

    private Appointment readyForCheckout(User customer, String petType, GroomingItem item) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName("毛孩" + UUID.randomUUID().toString().substring(0, 6))
                .petType(petType)
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

    private GroomingItem item(int price, PerformanceCategory category) {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code)
                .name("測試項目" + code)
                .price((double) price)
                .performanceCategory(category)
                .build());
    }

    private static CheckoutRequest pay(PaymentMethod method) {
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(method);
        return req;
    }

    private static DepositRequest deposit(int amount) {
        DepositRequest req = new DepositRequest();
        req.setAmount(amount);
        return req;
    }

    private int balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }
}
