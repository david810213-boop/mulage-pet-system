package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse.ItemLine;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 結帳折扣（預約結帳、現場單結帳）：首次體驗優惠、貓咪 90 天回洗優惠、會員儲值折扣三者逐項目擇優不疊加；
 * 特殊優惠不限付款方式，會員折扣只限儲值金付款且項目可享折扣。消費明細標示實際套用的折扣種類。
 */
class DiscountCheckoutTest extends IntegrationTestBase {

    private static final int GOLD = 15000;   // 9 折，跟回洗優惠同價時以回洗優惠計
    private static final int VIP = 50000;    // 85 折
    private static final int NORMAL = 8000;  // 95 折

    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private CheckoutPreviewService checkoutPreviewService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    // ── 預約結帳 ─────────────────────────────────────────────────────────

    @Test
    void rewashAppliesToCatBathOnlyForCash() {
        User owner = testData.customer();
        Appointment appointment = catWithRecentBath(owner, 1300,
                item(1000, PerformanceCategory.BATH_CAT_S), item(300, PerformanceCategory.OTHER));

        assertThat(checkout(appointment, PaymentMethod.CASH)).isEqualTo(900 + 300);
        assertThat(detailTypes(appointment, owner)).containsExactly(DiscountType.REWASH, DiscountType.NONE);
    }

    @Test
    void rewashWinsTieWithGoldCardAndMemberDiscountCoversOtherItems() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(GOLD));
        Appointment appointment = catWithRecentBath(owner, 1300,
                item(1000, PerformanceCategory.BATH_CAT_S), item(300, PerformanceCategory.OTHER));

        assertThat(previewWallet(appointment, owner)).isEqualTo(900 + 270);
        assertThat(checkout(appointment, PaymentMethod.WALLET)).isEqualTo(900 + 270);
        assertThat(detailTypes(appointment, owner)).containsExactly(DiscountType.REWASH, DiscountType.MEMBER);
    }

    @Test
    void vipDiscountBeatsRewash() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(VIP));
        Appointment appointment = catWithRecentBath(owner, 1300,
                item(1000, PerformanceCategory.BATH_CAT_S), item(300, PerformanceCategory.OTHER));

        assertThat(previewWallet(appointment, owner)).isEqualTo(850 + 255);
        assertThat(checkout(appointment, PaymentMethod.WALLET)).isEqualTo(850 + 255);
        assertThat(detailTypes(appointment, owner)).containsExactly(DiscountType.MEMBER, DiscountType.MEMBER);
    }

    @Test
    void itemsNotEligibleForMemberDiscountKeepFullPriceWithWallet() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(VIP));
        GroomingItem addOn = item(300, PerformanceCategory.OTHER);
        addOn.setDiscountEligible(false);
        groomingItemRepository.save(addOn);
        Appointment appointment = appointment(owner, "CAT", 1300, item(1000, PerformanceCategory.OTHER), addOn);

        assertThat(checkout(appointment, PaymentMethod.WALLET)).isEqualTo(850 + 300);
        assertThat(detailTypes(appointment, owner)).containsExactly(DiscountType.MEMBER, DiscountType.NONE);
    }

    @Test
    void catFirstVisitOnCheckinItemsAndRetailKeepsFullPrice() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(NORMAL));
        Appointment appointment = appointment(owner, "CAT", 1200);
        GroomingItem bath = item(1000, PerformanceCategory.BATH_CAT_L);
        checkinItem(appointment, bath, 1000, PerformanceCategory.BATH_CAT_L);
        checkinItem(appointment, null, 200, null); // 零售商品加購

        assertThat(previewWallet(appointment, owner)).isEqualTo(900 + 200);
        assertThat(checkout(appointment, PaymentMethod.WALLET)).isEqualTo(900 + 200);
        assertThat(detailTypes(appointment, owner)).containsExactly(DiscountType.FIRST_VISIT, DiscountType.NONE);
    }

    @Test
    void catFirstVisitForCashOnCheckinItems() {
        User owner = testData.customer();
        Appointment appointment = appointment(owner, "CAT", 1500);
        checkinItem(appointment, item(1000, PerformanceCategory.BATH_CAT_S), 1000, PerformanceCategory.BATH_CAT_S);
        checkinItem(appointment, item(500, PerformanceCategory.TRIM), 500, PerformanceCategory.TRIM);

        assertThat(checkout(appointment, PaymentMethod.CASH)).isEqualTo(900 + 500);
    }

    @Test
    void withoutAnySpecialOfferCashChargesTheBookedTotal() {
        User owner = testData.customer();
        Appointment appointment = appointment(owner, "CAT", 1234, item(1000, PerformanceCategory.BATH_CAT_S));
        paidAppointment(owner, appointment.getPetName(), LocalDate.now().minusYears(1), item(500, PerformanceCategory.OTHER));

        assertThat(checkout(appointment, PaymentMethod.CASH)).isEqualTo(1234);
    }

    // ── 現場單結帳 ───────────────────────────────────────────────────────

    @Test
    void walkInDogFirstVisitForCash() {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        Long orderId = walkIn(owner, dog.getName(),
                item(1000, PerformanceCategory.BATH_SMALL), item(300, PerformanceCategory.OTHER));

        WalkInOrderResponse result = walkInOrderService.checkout(orderId, PaymentMethod.CASH, testData.staff().getUsername());

        assertThat(result.getChargedAmount()).isEqualTo(900 + 300);
        assertThat(result.getItems()).extracting(ItemLine::isFirstVisitEligible).containsExactly(true, false);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.FIRST_VISIT, DiscountType.NONE);
    }

    @Test
    void walkInCatRewashTiesWithGoldCard() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(GOLD));
        PetResponse cat = testData.cat(owner);
        paidAppointment(owner, cat.getName(), LocalDate.now().minusDays(30), item(1000, PerformanceCategory.BATH_CAT_S));
        Long orderId = walkIn(owner, cat.getName(),
                item(1000, PerformanceCategory.BATH_CAT_S), item(300, PerformanceCategory.OTHER));

        assertThat(previewWalkIn(orderId, owner, "CAT")).isEqualTo(900 + 270);
        WalkInOrderResponse result = walkInOrderService.checkout(orderId, PaymentMethod.WALLET, testData.staff().getUsername());

        assertThat(result.getChargedAmount()).isEqualTo(900 + 270);
        assertThat(result.getItems()).extracting(ItemLine::isRewashEligible).containsExactly(true, false);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.REWASH, DiscountType.MEMBER);
    }

    @Test
    void walkInVipBeatsRewashAndCashGetsRewashOnly() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(VIP));
        PetResponse cat = testData.cat(owner);
        paidAppointment(owner, cat.getName(), LocalDate.now().minusDays(30), item(1000, PerformanceCategory.BATH_CAT_S));
        Long walletOrder = walkIn(owner, cat.getName(),
                item(1000, PerformanceCategory.BATH_CAT_S), item(300, PerformanceCategory.OTHER));

        assertThat(previewWalkIn(walletOrder, owner, "CAT")).isEqualTo(850 + 255);
        WalkInOrderResponse viaWallet = walkInOrderService.checkout(walletOrder, PaymentMethod.WALLET, testData.staff().getUsername());
        assertThat(viaWallet.getChargedAmount()).isEqualTo(850 + 255);
        assertThat(viaWallet.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.MEMBER, DiscountType.MEMBER);

        Long cashOrder = walkIn(owner, cat.getName(),
                item(1000, PerformanceCategory.BATH_CAT_L), item(300, PerformanceCategory.OTHER));
        WalkInOrderResponse viaCash = walkInOrderService.checkout(cashOrder, PaymentMethod.CASH, testData.staff().getUsername());
        assertThat(viaCash.getChargedAmount()).isEqualTo(900 + 300);
        assertThat(viaCash.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.REWASH, DiscountType.NONE);
    }

    @Test
    void walkInCatFirstVisitWithNormalCard() {
        User owner = testData.customer();
        walletService.deposit(owner.getUsername(), deposit(NORMAL));
        PetResponse cat = testData.cat(owner);
        Long orderId = walkIn(owner, cat.getName(),
                item(1000, PerformanceCategory.BATH_CAT_S), item(200, PerformanceCategory.OTHER));

        WalkInOrderResponse result = walkInOrderService.checkout(orderId, PaymentMethod.WALLET, testData.staff().getUsername());

        assertThat(result.getChargedAmount()).isEqualTo(900 + 190);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.FIRST_VISIT, DiscountType.MEMBER);
    }

    @Test
    void walkInWithoutMemberHasNoDiscount() {
        Long orderId = walkIn(null, "路過的狗", item(1000, PerformanceCategory.BATH_SMALL));

        WalkInOrderResponse result = walkInOrderService.checkout(orderId, PaymentMethod.CASH, testData.staff().getUsername());

        assertThat(result.getChargedAmount()).isEqualTo(1000);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType).containsExactly(DiscountType.NONE);
    }

    @Test
    void unpaidWalkInShowsEligibilityButNoAppliedDiscount() {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        Long orderId = walkIn(owner, dog.getName(), item(1000, PerformanceCategory.BATH_LARGE));

        WalkInOrderResponse order = transactionTemplate.execute(s -> walkInOrderService.getById(orderId));

        assertThat(order.getItems()).singleElement().satisfies(line -> {
            assertThat(line.isFirstVisitEligible()).isTrue();
            assertThat(line.getAppliedDiscountType()).isNull();
        });
    }

    // ── 輔助方法 ─────────────────────────────────────────────────────────

    private int checkout(Appointment appointment, PaymentMethod method) {
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(method);
        return paymentService.checkout(appointment.getId(), req, testData.staff().getUsername()).getFinalAmount();
    }

    private List<DiscountType> detailTypes(Appointment appointment, User owner) {
        return detail(appointment, owner).stream().map(DetailItem::getAppliedDiscountType).toList();
    }

    private List<DetailItem> detail(Appointment appointment, User owner) {
        return transactionTemplate.execute(s ->
                appointmentService.getAppointmentDetail(appointment.getId(), owner.getUsername()).getItems());
    }

    private int previewWallet(Appointment appointment, User owner) {
        return transactionTemplate.execute(s -> checkoutPreviewService.forAppointment(owner.getUsername(),
                appointmentService.getAppointmentDetail(appointment.getId(), owner.getUsername()).getItems(),
                appointment.getPetType()).finalAmount());
    }

    private int previewWalkIn(Long orderId, User owner, String petType) {
        return transactionTemplate.execute(s -> checkoutPreviewService.forWalkIn(owner.getUsername(),
                walkInOrderService.getById(orderId).getItems(), petType).finalAmount());
    }

    /** 30 天前（以這筆預約的日期為準）洗過澡的貓。 */
    private Appointment catWithRecentBath(User owner, int total, GroomingItem... items) {
        Appointment appointment = appointment(owner, "CAT", total, items);
        paidAppointment(owner, appointment.getPetName(), appointment.getDate().minusDays(30),
                item(1000, PerformanceCategory.BATH_CAT_S));
        return appointment;
    }

    private Appointment appointment(User owner, String petType, int total, GroomingItem... items) {
        return appointmentRepository.save(Appointment.builder()
                .user(owner).petName("折扣毛孩" + UUID.randomUUID().toString().substring(0, 6)).petType(petType)
                .date(TestData.uniqueFutureDate()).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(items)))
                .totalAmount(total).paid(false).status(AppointmentStatus.IN_PROGRESS).finalCheckDone(true)
                .build());
    }

    private void paidAppointment(User owner, String petName, LocalDate date, GroomingItem item) {
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("CAT")
                .date(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(item)))
                .totalAmount(item.getPrice().intValue()).paid(true).status(AppointmentStatus.COMPLETED)
                .build());
    }

    private void checkinItem(Appointment appointment, GroomingItem source, int price, PerformanceCategory category) {
        appointmentItemRepository.save(AppointmentItem.builder()
                .appointment(appointment)
                .groomingItemId(source != null ? source.getId() : null)
                .retailProductId(source == null ? 1L : null)
                .itemName(source != null ? source.getName() : "加購商品")
                .price(price).performanceCategory(category).build());
    }

    private Long walkIn(User member, String petName, GroomingItem... items) {
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(member != null ? member.getUsername() : null);
        req.setPetName(petName);
        List<WalkInOrderCreateRequest.Item> lines = new ArrayList<>();
        for (GroomingItem item : items) {
            WalkInOrderCreateRequest.Item line = new WalkInOrderCreateRequest.Item();
            line.setItemCode(item.getItemCode());
            lines.add(line);
        }
        req.setItems(lines);
        req.setRetailItems(new ArrayList<>());
        Long id = walkInOrderService.create(req, testData.staff().getUsername()).getId();
        WalkInOrder order = walkInOrderRepository.findById(id).orElseThrow();
        order.setServiceEndedDone(true);
        order.setFinalCheckDone(true);
        walkInOrderRepository.save(order);
        return id;
    }

    private GroomingItem item(int price, PerformanceCategory category) {
        String code = "Z" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("折扣測試項目" + code).price((double) price).points(1.0)
                .performanceCategory(category).build());
    }

    private static DepositRequest deposit(int amount) {
        DepositRequest req = new DepositRequest();
        req.setAmount(amount);
        return req;
    }
}
