package com.petgrooming.pet_system.service;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.DepositPaymentRequest;
import com.petgrooming.pet_system.dto.DepositPaymentView;
import com.petgrooming.pet_system.dto.DepositStatusResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DepositRefundStatus;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.DepositPayment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.DepositPaymentRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 預約訂金：同日多隻、首次大型犬、儲值金會員免收、回報匯款與核帳、逾期取消、取消退款、改期。
 * 測試日期都在遙遠的未來，取消一律落在「72 小時以上、全額退還」；退款級距見 DepositPolicyTest。
 */
class BookingDepositFlowTest extends IntegrationTestBase {

    private static final LocalTime NOON = LocalTime.of(12, 0);
    private static final LocalTime TWO_PM = LocalTime.of(14, 0);

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private BookingDepositService bookingDepositService;

    @Autowired
    private SlotCapacityService slotCapacityService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private DepositPaymentRepository depositPaymentRepository;

    @Autowired
    private TestData testData;

    @Test
    void singleSmallPetNeedsNoDeposit() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);

        AppointmentResponse booked = book(customer, dog, testData.openFutureDate(), NOON);

        assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(booked.getDepositShare()).isZero();
    }

    @Test
    void secondPetSameDayAwaitsDepositAndPaymentConfirmsTheWholeDay() {
        User customer = testData.customer();
        User staff = testData.staff();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse first = book(customer, testData.dog(customer), date, NOON);
        AppointmentResponse second = book(customer, testData.cat(customer), date, TWO_PM);

        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.AWAITING_DEPOSIT);
        assertThat(second.getDepositDueAt()).isNotNull();
        assertThat(reload(first).getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(reload(first).getDepositShare()).isEqualTo(500);
        assertThat(bookingDepositService.status(customer.getUsername(), date).outstanding()).isEqualTo(1000);

        DepositStatusResponse submitted = bookingDepositService.submitPayment(customer.getUsername(),
                new DepositPaymentRequest(date, "12345", null));
        assertThat(submitted.paymentPending()).isTrue();
        assertThatThrownBy(() -> bookingDepositService.submitPayment(customer.getUsername(),
                new DepositPaymentRequest(date, "12345", null)))
                .isInstanceOf(AppointmentException.class);

        Long paymentId = pendingPaymentId(customer, date);
        DepositPaymentView confirmed = bookingDepositService.confirmPayment(paymentId, staff.getName());

        assertThat(confirmed.outstandingNow()).isZero();
        assertThat(reload(first).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reload(second).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reload(first).getDepositPaid()).isEqualTo(500);
        assertThat(reload(second).getDepositPaid()).isEqualTo(500);
        assertThatThrownBy(() -> bookingDepositService.confirmPayment(paymentId, staff.getName()))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void firstVisitLargeDogAwaitsDepositEvenAlone() {
        User customer = testData.customer();

        AppointmentResponse booked = book(customer, testData.largeDog(customer), testData.openFutureDate(), NOON);

        assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.AWAITING_DEPOSIT);
        assertThat(booked.getDepositShare()).isEqualTo(1000);
    }

    @Test
    void largeDogWithCatPaysFifteenHundred() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        book(customer, testData.largeDog(customer), date, NOON);
        book(customer, testData.cat(customer), date, TWO_PM);

        assertThat(bookingDepositService.status(customer.getUsername(), date).outstanding()).isEqualTo(1500);
    }

    @Test
    void storedValueMemberIsExempt() {
        User customer = testData.customer();
        walletRepository.save(Wallet.builder().user(customer).balance(3000).build());
        LocalDate date = testData.openFutureDate();
        book(customer, testData.dog(customer), date, NOON);

        AppointmentResponse second = book(customer, testData.cat(customer), date, TWO_PM);

        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(bookingDepositService.preview(customer.getUsername(), testData.dog(customer).getId(), date).exempt())
                .isTrue();
    }

    @Test
    void previewMatchesWhatTheNextBookingWillCost() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        book(customer, testData.dog(customer), date, NOON);

        assertThat(bookingDepositService.preview(customer.getUsername(), testData.cat(customer).getId(), date).amount())
                .isEqualTo(1000);
    }

    @Test
    void overdueDepositCancelsTheBookingAndReleasesTheSeat() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse first = book(customer, testData.dog(customer), date, NOON);
        AppointmentResponse second = book(customer, testData.cat(customer), date, TWO_PM);
        Appointment overdue = reload(second);
        overdue.setDepositDueAt(LocalDateTime.now().minusMinutes(1));
        appointmentRepository.save(overdue);

        bookingDepositService.expireOverdue();

        assertThat(reload(second).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(slotCapacityService.bookedCount(date, TWO_PM)).isZero();
        assertThat(reload(first).getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(reload(first).getDepositShare()).isZero();
    }

    @Test
    void reportedPaymentBeforeDeadlineIsNotExpired() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse booked = book(customer, testData.largeDog(customer), date, NOON);
        bookingDepositService.submitPayment(customer.getUsername(), new DepositPaymentRequest(date, "54321", null));
        DepositPayment payment = depositPaymentRepository.findById(pendingPaymentId(customer, date)).orElseThrow();
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        depositPaymentRepository.save(payment);
        Appointment a = reload(booked);
        a.setDepositDueAt(LocalDateTime.now().minusMinutes(1));
        appointmentRepository.save(a);

        bookingDepositService.expireOverdue();

        assertThat(reload(booked).getStatus()).isEqualTo(AppointmentStatus.AWAITING_DEPOSIT);
    }

    @Test
    void customerCannotSelfCancelAfterPayingButStaffCanWithRefund() {
        User customer = testData.customer();
        User staff = testData.staff();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse booked = paidLargeDog(customer, staff, date);

        assertThatThrownBy(() -> appointmentService.cancel(booked.getId(), new CancelAppointmentRequest(),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("官方 LINE");

        appointmentService.cancel(booked.getId(), new CancelAppointmentRequest(), staff.getUsername());

        Appointment cancelled = reload(booked);
        assertThat(cancelled.getDepositRefundAmount()).isEqualTo(1000);
        assertThat(cancelled.getDepositRefundStatus()).isEqualTo(DepositRefundStatus.PENDING);
        bookingDepositService.markRefunded(booked.getId());
        assertThat(reload(booked).getDepositRefundStatus()).isEqualTo(DepositRefundStatus.DONE);
    }

    @Test
    void customerCanCancelUnpaidAwaitingBooking() {
        User customer = testData.customer();
        AppointmentResponse booked = book(customer, testData.largeDog(customer), testData.openFutureDate(), NOON);

        appointmentService.cancel(booked.getId(), new CancelAppointmentRequest(), customer.getUsername());

        assertThat(reload(booked).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(reload(booked).getDepositRefundAmount()).isZero();
    }

    @Test
    void staffCannotManuallyConfirmWithoutDeposit() {
        User customer = testData.customer();
        User staff = testData.staff();
        AppointmentResponse booked = book(customer, testData.largeDog(customer), testData.openFutureDate(), NOON);

        assertThatThrownBy(() -> appointmentService.confirm(booked.getId(), null, staff.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("訂金");
    }

    @Test
    void waivingConfirmsTheDay() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse booked = book(customer, testData.largeDog(customer), date, NOON);

        bookingDepositService.waive(customer.getId(), date);

        assertThat(reload(booked).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reload(booked).getDepositShare()).isZero();
    }

    @Test
    void waivingTwoPetsSameDayConfirmsBoth() {
        User customer = testData.customer();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse first = book(customer, testData.dog(customer), date, NOON);
        AppointmentResponse second = book(customer, testData.cat(customer), date, TWO_PM);

        assertThat(bookingDepositService.waive(customer.getId(), date)).isEqualTo(2);

        assertThat(reload(first).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reload(second).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void paidBookingCanBeRescheduledOnlyOnceAndDepositMoves() {
        User customer = testData.customer();
        User staff = testData.staff();
        LocalDate date = testData.openFutureDate();
        AppointmentResponse booked = paidLargeDog(customer, staff, date);
        LocalDate newDate = testData.openFutureDate();

        appointmentService.reschedule(booked.getId(), newDate, NOON, staff.getUsername());

        Appointment moved = reload(booked);
        assertThat(moved.getDate()).isEqualTo(newDate);
        assertThat(moved.getDepositPaid()).isEqualTo(1000);
        assertThat(moved.getRescheduleCount()).isEqualTo(1);
        assertThat(slotCapacityService.bookedCount(date, NOON)).isZero();
        assertThat(slotCapacityService.bookedCount(newDate, NOON)).isEqualTo(1);

        LocalDate thirdDate = testData.openFutureDate();
        assertThatThrownBy(() -> appointmentService.reschedule(booked.getId(), thirdDate, NOON, staff.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("一次");
    }

    @Test
    void creditIsAppliedAtCheckoutAndExcessBecomesRefund() {
        User customer = testData.customer();
        User staff = testData.staff();
        AppointmentResponse booked = paidLargeDog(customer, staff, testData.openFutureDate());
        Appointment a = reload(booked);

        int applied = bookingDepositService.applyAtCheckout(a, 600);

        assertThat(applied).isEqualTo(600);
        assertThat(a.getDepositRefundAmount()).isEqualTo(400);
        assertThat(a.getDepositRefundStatus()).isEqualTo(DepositRefundStatus.PENDING);
        assertThat(a.depositCredit()).isZero();
    }

    private AppointmentResponse paidLargeDog(User customer, User staff, LocalDate date) {
        AppointmentResponse booked = book(customer, testData.largeDog(customer), date, NOON);
        bookingDepositService.submitPayment(customer.getUsername(), new DepositPaymentRequest(date, "12345", null));
        bookingDepositService.confirmPayment(pendingPaymentId(customer, date), staff.getName());
        assertThat(reload(booked).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        return booked;
    }

    private AppointmentResponse book(User customer, PetResponse pet, LocalDate date, LocalTime start) {
        return appointmentService.book(bookingRequest(pet.getId(), date, start), customer.getUsername());
    }

    private Appointment reload(AppointmentResponse a) {
        return appointmentRepository.findById(a.getId()).orElseThrow();
    }

    private Long pendingPaymentId(User customer, LocalDate date) {
        return bookingDepositService.pendingPayments().stream()
                .filter(p -> p.userId().equals(customer.getId()) && p.date().equals(date))
                .findFirst().orElseThrow().id();
    }
}
