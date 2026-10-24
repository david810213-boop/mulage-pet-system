package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse;
import com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.DogWeightTier;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 預約明細（LIFF 與後台共用）：項目、實際套用的折扣標籤、實收金額、付款資訊與權限。
 */
class AppointmentDetailTest extends IntegrationTestBase {

    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentDetailService appointmentDetailService;
    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;
    @Autowired private RetailProductService retailProductService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    @Test
    void walletPaidBookingShowsMemberDiscountAndChargedAmount() {
        User staff = testData.staff();
        User member = testData.customer();
        walletService.deposit(member.getUsername(), deposit(15000)); // 金卡 9 折
        Appointment appointment = appointment(member, "CAT", 1000);
        appointment.setSelectedItems(new ArrayList<>(List.of(groomingItem(1000, PerformanceCategory.OTHER, null))));
        appointmentRepository.save(appointment);
        paymentService.checkout(appointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername());

        AppointmentDetailResponse detail = detail(appointment.getId(), member);

        assertThat(detail.getAppointmentCode()).isEqualTo(String.format("AP%03d", appointment.getId()));
        assertThat(detail.isPaid()).isTrue();
        assertThat(detail.getTotalAmount()).isEqualTo(1000);
        assertThat(detail.getChargedAmount()).isEqualTo(900);
        assertThat(detail.getPaymentMethodLabel()).isEqualTo(PaymentMethod.WALLET.getDisplayName());
        assertThat(detail.getHandledBy()).isEqualTo("員工：" + staff.getName());
        assertThat(detail.getItems()).singleElement().satisfies(item -> {
            assertThat(item.isDiscountEligible()).isTrue();
            assertThat(item.getAppliedDiscountType()).isEqualTo(DiscountType.MEMBER);
        });
    }

    @Test
    void checkinItemsShowOperatorWeightTierFirstVisitAndRetail() {
        User staff = testData.staff();
        User customer = testData.customer();
        DogWeightTier tier = DogWeightTier.values()[0];
        GroomingItem bath = groomingItem(1000, PerformanceCategory.BATH_SMALL, tier);
        RetailProduct shampoo = retailProductService.create("明細測試商品-" + UUID.randomUUID().toString().substring(0, 6),
                300, 5, null, 80);
        Appointment appointment = appointment(customer, "DOG", 1300);
        appointmentItemRepository.save(AppointmentItem.builder().appointment(appointment)
                .groomingItemId(bath.getId()).itemName("小型犬洗澡").price(1000)
                .performanceCategory(PerformanceCategory.BATH_SMALL).operatorStaff(staff).build());
        appointmentItemRepository.save(AppointmentItem.builder().appointment(appointment)
                .retailProductId(shampoo.getId()).itemName("洗毛精").price(300).build());
        paymentService.checkout(appointment.getId(), pay(PaymentMethod.CASH), staff.getUsername());

        List<DetailItem> items = detail(appointment.getId(), customer).getItems();

        assertThat(items).hasSize(2);
        DetailItem bathLine = items.stream().filter(i -> !i.isRetailItem()).findFirst().orElseThrow();
        assertThat(bathLine.getGroomingItemId()).isEqualTo(bath.getId());
        assertThat(bathLine.getDogWeightTier()).isEqualTo(tier.name());
        assertThat(bathLine.getOperatorName()).isEqualTo(staff.getName());
        assertThat(bathLine.isFirstVisitEligible()).isTrue();
        assertThat(bathLine.getAppliedDiscountType()).isEqualTo(DiscountType.FIRST_VISIT);
        DetailItem retailLine = items.stream().filter(DetailItem::isRetailItem).findFirst().orElseThrow();
        assertThat(retailLine.getGroomingItemId()).isNull();
        assertThat(retailLine.getAppliedDiscountType()).isEqualTo(DiscountType.NONE);
    }

    @Test
    void unpaidFirstVisitIsQuotePendingWithoutPaymentInfo() {
        User customer = testData.customer();
        var dog = testData.dog(customer);
        AppointmentResponse booked = appointmentService.book(
                bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.NOON), customer.getUsername());

        AppointmentDetailResponse detail = detail(booked.getId(), customer);

        assertThat(detail.isQuotePending()).isTrue();
        assertThat(detail.isPaid()).isFalse();
        assertThat(detail.getChargedAmount()).isNull();
        assertThat(detail.getPaymentMethodLabel()).isNull();
        assertThat(detail.getItems()).isEmpty();
    }

    @Test
    void onlyOwnerOrStaffCanSeeTheDetail() {
        User owner = testData.customer();
        Appointment appointment = appointment(owner, "CAT", 0);

        assertThatThrownBy(() -> detail(appointment.getId(), testData.customer()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("權限不足");
        assertThat(detail(appointment.getId(), testData.staff()).getPetName()).isEqualTo(appointment.getPetName());
    }

    private AppointmentDetailResponse detail(Long appointmentId, User viewer) {
        return transactionTemplate.execute(status ->
                appointmentDetailService.getAppointmentDetail(appointmentId, viewer.getUsername()));
    }

    private Appointment appointment(User customer, String petType, int total) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName("明細毛孩" + UUID.randomUUID().toString().substring(0, 6))
                .petType(petType)
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(14, 0))
                .totalAmount(total)
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .finalCheckDone(true)
                .build());
    }

    private GroomingItem groomingItem(int price, PerformanceCategory category, DogWeightTier tier) {
        String code = "D" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("明細測試項目" + code).price((double) price)
                .performanceCategory(category).dogWeightTier(tier).build());
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
}
