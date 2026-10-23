package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 選服務項目的預約：既有客戶的毛孩、店員代客預約。驗證項目解析、下架項目、僅限既有客戶項目與金額計算。
 */
class AppointmentBookingWithItemsTest extends IntegrationTestBase {

    private static final LocalTime TWO_PM = LocalTime.of(14, 0);

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private SlotCapacityService slotCapacityService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private GroomingItemRepository groomingItemRepository;

    @Autowired
    private TestData testData;

    @Test
    void returningPetBookingTotalsSelectedItems() {
        User customer = testData.customer();
        PetResponse dog = returningDog(customer);
        GroomingItem bath = item(1200, false);
        GroomingItem nails = item(300, false);
        LocalDate date = testData.openFutureDate();

        AppointmentResponse booked = appointmentService.book(
                withItems(dog, date, bath, nails), customer.getUsername());

        assertThat(booked.isFirstVisitAssessment()).isFalse();
        assertThat(booked.getTotalAmount()).isEqualTo(1500);
        assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRM);
        assertThat(slotCapacityService.bookedCount(date, TWO_PM)).isEqualTo(1);
    }

    @Test
    void returningPetMustSelectAtLeastOneItem() {
        User customer = testData.customer();
        PetResponse dog = returningDog(customer);
        LocalDate date = testData.openFutureDate();

        assertThatThrownBy(() -> appointmentService.book(withItems(dog, date), customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("至少選擇一項");
        assertThat(slotCapacityService.bookedCount(date, TWO_PM)).isZero();
    }

    @Test
    void discontinuedItemIsRejectedAndSeatIsReturned() {
        User customer = testData.customer();
        PetResponse dog = returningDog(customer);
        GroomingItem retired = item(800, false);
        retired.setDeleted(true);
        groomingItemRepository.save(retired);
        LocalDate date = testData.openFutureDate();

        assertThatThrownBy(() -> appointmentService.book(withItems(dog, date, retired), customer.getUsername()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("已下架");
        assertThat(slotCapacityService.bookedCount(date, TWO_PM)).isZero();
    }

    @Test
    void staffAssistedFirstVisitWithItemsIsNormalBooking() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        GroomingItem bath = item(1000, false);

        AppointmentResponse booked = appointmentService.book(
                withItems(dog, testData.openFutureDate(), bath), customer.getUsername(), true);

        assertThat(booked.isFirstVisitAssessment()).isFalse();
        assertThat(booked.getTotalAmount()).isEqualTo(1000);
    }

    @Test
    void staffAssistedFirstVisitWithoutItemsIsAssessment() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);

        AppointmentResponse booked = appointmentService.book(
                withItems(dog, testData.openFutureDate()), customer.getUsername(), true);

        assertThat(booked.isFirstVisitAssessment()).isTrue();
        assertThat(booked.getTotalAmount()).isZero();
    }

    @Test
    void existingCustomerOnlyItemIsRejectedForFirstVisit() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        GroomingItem membersOnly = item(500, true);

        assertThatThrownBy(() -> appointmentService.book(
                withItems(dog, testData.openFutureDate(), membersOnly), customer.getUsername(), true))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("僅限既有客戶");
    }

    @Test
    void existingCustomerOnlyItemIsAllowedForReturningPet() {
        User customer = testData.customer();
        PetResponse dog = returningDog(customer);
        GroomingItem membersOnly = item(500, true);

        AppointmentResponse booked = appointmentService.book(
                withItems(dog, testData.openFutureDate(), membersOnly), customer.getUsername());

        assertThat(booked.getTotalAmount()).isEqualTo(500);
    }

    @Test
    void assignedStaffIsRecorded() {
        User customer = testData.customer();
        User groomer = testData.staff();
        PetResponse dog = returningDog(customer);
        AppointmentRequest req = withItems(dog, testData.openFutureDate(), item(900, false));
        req.setStaffId(groomer.getId());

        AppointmentResponse booked = appointmentService.book(req, customer.getUsername());

        Appointment saved = appointmentRepository.findById(booked.getId()).orElseThrow();
        assertThat(saved.getStaff().getId()).isEqualTo(groomer.getId());
    }

    /** 已有一筆已付款預約的毛孩（既有客戶）。 */
    private PetResponse returningDog(User owner) {
        PetResponse dog = testData.dog(owner);
        appointmentRepository.save(Appointment.builder()
                .user(owner)
                .petName(dog.getName())
                .petType("DOG")
                .date(LocalDate.now().minusMonths(1))
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(14, 0))
                .totalAmount(1000)
                .paid(true)
                .status(AppointmentStatus.COMPLETED)
                .build());
        return dog;
    }

    private GroomingItem item(int price, boolean existingCustomersOnly) {
        String code = "B" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code)
                .name("預約測試項目" + code)
                .price((double) price)
                .requiresExistingCustomer(existingCustomersOnly)
                .build());
    }

    private static AppointmentRequest withItems(PetResponse pet, LocalDate date, GroomingItem... items) {
        AppointmentRequest req = bookingRequest(pet.getId(), date, TWO_PM);
        req.setSelectedItems(List.of(items).stream().map(GroomingItem::getItemCode).toList());
        return req;
    }
}
