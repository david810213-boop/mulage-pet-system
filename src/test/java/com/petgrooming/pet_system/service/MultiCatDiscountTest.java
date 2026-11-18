package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 多貓優惠：同一位會員同一天兩位以上貓咪做大美容，大美容 9 折；跟會員儲值折扣擇優不疊加。
 */
class MultiCatDiscountTest extends IntegrationTestBase {

    private static final int VIP = 50000; // 85 折

    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;
    @Autowired private AppointmentDetailService appointmentDetailService;
    @Autowired private CheckoutPreviewService checkoutPreviewService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    @Test
    void twoCatsFullGroomSameDayGetNinetyPercent() {
        User owner = testData.customer();
        LocalDate day = TestData.uniqueFutureDate();
        Appointment first = returningCat(owner, day, fullGroom(2000), item(300, PerformanceCategory.OTHER));
        returningCat(owner, day, fullGroom(2200));

        assertThat(checkout(first, PaymentMethod.CASH)).isEqualTo(1800 + 300);
        assertThat(detail(first, owner)).extracting(DetailItem::getAppliedDiscountType)
                .containsExactly(DiscountType.MULTI_CAT, DiscountType.NONE);
    }

    @Test
    void secondCatWithoutFullGroomDoesNotQualify() {
        User owner = testData.customer();
        LocalDate day = TestData.uniqueFutureDate();
        Appointment first = returningCat(owner, day, fullGroom(2000));
        returningCat(owner, day, item(1500, PerformanceCategory.BATH_CAT_S));

        assertThat(checkout(first, PaymentMethod.CASH)).isEqualTo(2000);
    }

    @Test
    void cancelledOrOtherDayCatsDoNotCount() {
        User owner = testData.customer();
        LocalDate day = TestData.uniqueFutureDate();
        Appointment first = returningCat(owner, day, fullGroom(2000));
        Appointment cancelled = returningCat(owner, day, fullGroom(2000));
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(cancelled);
        returningCat(owner, day.plusDays(1), fullGroom(2000));

        assertThat(checkout(first, PaymentMethod.CASH)).isEqualTo(2000);
    }

    @Test
    void vipDiscountBeatsMultiCat() {
        User owner = testData.customer();
        DepositRequest deposit = new DepositRequest();
        deposit.setAmount(VIP);
        walletService.deposit(owner.getUsername(), deposit);
        LocalDate day = TestData.uniqueFutureDate();
        Appointment first = returningCat(owner, day, fullGroom(2000));
        returningCat(owner, day, fullGroom(2000));

        assertThat(previewWallet(first, owner)).isEqualTo(1700);
        assertThat(checkout(first, PaymentMethod.WALLET)).isEqualTo(1700);
        assertThat(detail(first, owner)).extracting(DetailItem::getAppliedDiscountType)
                .containsExactly(DiscountType.MEMBER);
    }

    /** 有一年前的消費紀錄（不算首次體驗，也超過 90 天回洗期間）的貓咪預約。 */
    private Appointment returningCat(User owner, LocalDate day, GroomingItem... items) {
        String petName = "多貓" + UUID.randomUUID().toString().substring(0, 6);
        GroomingItem old = item(500, PerformanceCategory.OTHER);
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("CAT")
                .date(LocalDate.now().minusYears(1)).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(old)))
                .totalAmount(500).paid(true).status(AppointmentStatus.COMPLETED)
                .build());
        int total = 0;
        for (GroomingItem i : items) {
            total += i.getPrice().intValue();
        }
        return appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("CAT")
                .date(day).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(items)))
                .totalAmount(total).paid(false).status(AppointmentStatus.IN_PROGRESS).finalCheckDone(true)
                .build());
    }

    private GroomingItem fullGroom(int price) {
        return save("大美容-測試-" + UUID.randomUUID().toString().substring(0, 6), price,
                PerformanceCategory.BATH_CAT_S);
    }

    private GroomingItem item(int price, PerformanceCategory category) {
        return save("多貓測試項目" + UUID.randomUUID().toString().substring(0, 6), price, category);
    }

    private GroomingItem save(String name, int price, PerformanceCategory category) {
        String code = "M" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name(name).price((double) price).points(1.0)
                .performanceCategory(category).build());
    }

    private int checkout(Appointment appointment, PaymentMethod method) {
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(method);
        return paymentService.checkout(appointment.getId(), req, testData.staff().getUsername()).getFinalAmount();
    }

    private List<DetailItem> detail(Appointment appointment, User owner) {
        return transactionTemplate.execute(s ->
                appointmentDetailService.getAppointmentDetail(appointment.getId(), owner.getUsername()).getItems());
    }

    private int previewWallet(Appointment appointment, User owner) {
        return transactionTemplate.execute(s -> checkoutPreviewService.forAppointment(owner.getUsername(),
                appointmentDetailService.getAppointmentDetail(appointment.getId(), owner.getUsername()).getItems(),
                appointment.getPetType()).finalAmount());
    }
}
