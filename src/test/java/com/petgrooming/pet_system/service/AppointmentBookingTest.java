package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 線上預約流程：以「初次預約只選時段」的路徑驗證日期、契約、歸屬、名額與物種限制等規則。
 */
class AppointmentBookingTest extends IntegrationTestBase {

    private static final LocalTime NOON = LocalTime.of(12, 0);

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private SlotCapacityService slotCapacityService;

    @Autowired
    private ClosedDateService closedDateService;

    @Autowired
    private TestData testData;

    @Test
    void firstVisitBookingIsPendingAssessmentAndTakesOneSeat() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        LocalDate date = testData.openFutureDate();

        AppointmentResponse booked = appointmentService.book(bookingRequest(dog.getId(), date, NOON),
                customer.getUsername());

        assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(booked.isFirstVisitAssessment()).isTrue();
        assertThat(booked.getTotalAmount()).isZero();
        assertThat(slotCapacityService.bookedCount(date, NOON)).isEqualTo(1);
    }

    @Test
    void secondOpenFirstVisitForSamePetIsRejectedAndDoesNotKeepTheSeat() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        appointmentService.book(bookingRequest(dog.getId(), testData.openFutureDate(), NOON),
                customer.getUsername());
        LocalDate secondDate = testData.openFutureDate();

        assertThatThrownBy(() -> appointmentService.book(bookingRequest(dog.getId(), secondDate, NOON),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("初次預約還沒完成");
        assertThat(slotCapacityService.bookedCount(secondDate, NOON)).isZero();
    }

    @Test
    void pastDateIsRejected() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);

        assertThatThrownBy(() -> appointmentService.book(
                bookingRequest(dog.getId(), LocalDate.now().minusDays(1), NOON), customer.getUsername()))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void customerCannotBookForSameDay() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);

        assertThatThrownBy(() -> appointmentService.book(
                bookingRequest(dog.getId(), LocalDate.now(), NOON), customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("不開放當天");
    }

    @Test
    void customerCannotBookAnotherCustomersPet() {
        User owner = testData.customer();
        User stranger = testData.customer();
        PetResponse dog = testData.dog(owner);
        LocalDate date = testData.openFutureDate();

        assertThatThrownBy(() -> appointmentService.book(bookingRequest(dog.getId(), date, NOON),
                stranger.getUsername()))
                .isInstanceOf(AppointmentException.class);
        assertThat(slotCapacityService.bookedCount(date, NOON)).isZero();
    }

    @Test
    void bookingWithoutSignedContractIsRejected() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        AppointmentRequest req = bookingRequest(dog.getId(), testData.openFutureDate(), NOON);
        req.setContractSignatureData("data:image/png;base64,short");

        assertThatThrownBy(() -> appointmentService.book(req, customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("簽名");
    }

    @Test
    void bookingOutsideBusinessHoursIsRejected() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);

        assertThatThrownBy(() -> appointmentService.book(
                bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.of(9, 0)),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("營業時間");
    }

    @Test
    void bookingOnClosedDateIsRejected() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        LocalDate date = testData.openFutureDate();
        closedDateService.setClosed(date, "測試公休");

        assertThatThrownBy(() -> appointmentService.book(bookingRequest(dog.getId(), date, NOON),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("公休");
    }

    @Test
    void fullSlotIsRejected() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        LocalDate date = testData.openFutureDate();
        slotCapacityService.setCapacity(date, NOON, 0);

        assertThatThrownBy(() -> appointmentService.book(bookingRequest(dog.getId(), date, NOON),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("額滿");
    }

    @Test
    void catOnlySlotRejectsDogs() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        LocalDate date = testData.openFutureDate();
        slotCapacityService.setCapacity(date, NOON, 3, PetType.CAT);

        assertThatThrownBy(() -> appointmentService.book(bookingRequest(dog.getId(), date, NOON),
                customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("僅開放");
        assertThat(slotCapacityService.bookedCount(date, NOON)).isZero();
    }

    @Test
    void cancellingReturnsTheSeatAndCannotBeRepeated() {
        User customer = testData.customer();
        PetResponse cat = testData.cat(customer);
        LocalDate date = testData.openFutureDate();
        AppointmentResponse booked = appointmentService.book(bookingRequest(cat.getId(), date, NOON),
                customer.getUsername());

        AppointmentResponse cancelled = appointmentService.cancel(booked.getId(), null, customer.getUsername());

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(slotCapacityService.bookedCount(date, NOON)).isZero();
        assertThatThrownBy(() -> appointmentService.cancel(booked.getId(), null, customer.getUsername()))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void customerCannotCancelAnotherCustomersAppointment() {
        User owner = testData.customer();
        User stranger = testData.customer();
        PetResponse dog = testData.dog(owner);
        AppointmentResponse booked = appointmentService.book(
                bookingRequest(dog.getId(), testData.openFutureDate(), NOON), owner.getUsername());

        assertThatThrownBy(() -> appointmentService.cancel(booked.getId(), null, stranger.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("權限不足");
    }
}
