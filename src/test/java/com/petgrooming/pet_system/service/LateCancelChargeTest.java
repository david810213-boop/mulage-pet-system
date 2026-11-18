package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 儲值金會員臨時取消：24 小時內不能自行取消，由店員取消並從儲值金扣款；沒有儲值金的顧客照舊可以自行取消。
 */
class LateCancelChargeTest extends IntegrationTestBase {

    @Autowired private AppointmentService appointmentService;
    @Autowired private WalletService walletService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TestData testData;

    @Test
    void staffCancelDeductsTheChargeFromWallet() {
        User member = memberWith(8000);
        int before = balance(member);
        Appointment a = appointment(member, LocalDate.now(), LocalTime.of(23, 59));

        appointmentService.cancel(a.getId(), request(660), testData.staff().getUsername());

        assertThat(balance(member)).isEqualTo(before - 660);
        Appointment saved = appointmentRepository.findById(a.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(saved.getLateCancelCharge()).isEqualTo(660);
    }

    @Test
    void chargeOverBalanceKeepsTheAppointment() {
        User member = memberWith(5000);
        int before = balance(member);
        Appointment a = appointment(member, LocalDate.now(), LocalTime.of(23, 59));

        assertThatThrownBy(() -> appointmentService.cancel(a.getId(), request(before + 1),
                testData.staff().getUsername()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(appointmentRepository.findById(a.getId()).orElseThrow().isCancelled()).isFalse();
        assertThat(balance(member)).isEqualTo(before);
    }

    @Test
    void walletMemberCannotSelfCancelWithin24Hours() {
        User member = memberWith(8000);
        Appointment a = appointment(member, LocalDate.now(), LocalTime.of(23, 59));

        assertThatThrownBy(() -> appointmentService.cancel(a.getId(), new CancelAppointmentRequest(),
                member.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("24 小時內");
    }

    @Test
    void walletMemberCanSelfCancelEarlierAndCustomerWithoutBalanceAnytime() {
        User member = memberWith(8000);
        Appointment early = appointment(member, LocalDate.now().plusDays(5), LocalTime.NOON);
        appointmentService.cancel(early.getId(), new CancelAppointmentRequest(), member.getUsername());
        assertThat(appointmentRepository.findById(early.getId()).orElseThrow().isCancelled()).isTrue();

        User plain = testData.customer();
        Appointment late = appointment(plain, LocalDate.now(), LocalTime.of(23, 59));
        appointmentService.cancel(late.getId(), new CancelAppointmentRequest(), plain.getUsername());
        assertThat(appointmentRepository.findById(late.getId()).orElseThrow().isCancelled()).isTrue();
    }

    @Test
    void customerCannotSetACharge() {
        User plain = testData.customer();
        Appointment a = appointment(plain, LocalDate.now().plusDays(5), LocalTime.NOON);

        appointmentService.cancel(a.getId(), request(500), plain.getUsername());

        assertThat(appointmentRepository.findById(a.getId()).orElseThrow().getLateCancelCharge()).isNull();
    }

    private User memberWith(int amount) {
        User member = testData.customer();
        DepositRequest deposit = new DepositRequest();
        deposit.setAmount(amount);
        walletService.deposit(member.getUsername(), deposit);
        return member;
    }

    private int balance(User member) {
        return walletService.getWallet(member.getUsername()).getBalance();
    }

    private Appointment appointment(User owner, LocalDate date, LocalTime start) {
        return appointmentRepository.save(Appointment.builder()
                .user(owner).petName("取消" + UUID.randomUUID().toString().substring(0, 6)).petType("CAT")
                .date(date).startTime(start).endTime(start)
                .selectedItems(new ArrayList<>())
                .totalAmount(2000).paid(false).status(AppointmentStatus.CONFIRMED)
                .build());
    }

    private static CancelAppointmentRequest request(int charge) {
        CancelAppointmentRequest req = new CancelAppointmentRequest();
        req.setReason("臨時取消");
        req.setLateCancelCharge(charge);
        return req;
    }
}
