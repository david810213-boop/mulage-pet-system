package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.FinancialReportResponse;
import com.petgrooming.pet_system.dto.TopUpRequestCreate;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.SupplyUsageRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.SupplyUsageRecordRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 財務報表：業績只算已結帳（預約＋現場單），儲值另計；零售營收、零售成本估算、洗劑成本。
 * 報表統計整個資料庫，因此以「加入已知資料前後的差額」驗證，不受其他測試資料影響。
 */
class FinancialReportTest extends IntegrationTestBase {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;
    @Autowired private TopUpService topUpService;
    @Autowired private RetailProductService retailProductService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private SupplyUsageRecordRepository supplyUsageRecordRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    @Test
    void reportReflectsPaidOrdersTopUpsRetailAndSupplyCosts() {
        User staff = testData.staff();
        User member = testData.customer();
        walletService.deposit(member.getUsername(), deposit(15000)); // 金卡 9 折，直接儲值不算儲值申請
        RetailProduct shampoo = retailProductService.create("報表測試商品-" + UUID.randomUUID().toString().substring(0, 6),
                300, 10, null, 80);

        FinancialReportResponse before = report();

        // 預約結帳（現金）：服務 1000 ＋ 加購零售商品 200
        Appointment cashAppointment = appointment(testData.customer(), 1200);
        checkinItem(cashAppointment, "洗澡", 1000, null);
        checkinItem(cashAppointment, "洗毛精", 200, shampoo.getId());
        paymentService.checkout(cashAppointment.getId(), pay(PaymentMethod.CASH), staff.getUsername());

        // 預約結帳（儲值金，金卡 9 折）：1000 → 900
        Appointment walletAppointment = appointment(member, 1000);
        walletAppointment.setSelectedItems(new ArrayList<>(List.of(groomingItem(1000))));
        appointmentRepository.save(walletAppointment);
        paymentService.checkout(walletAppointment.getId(), pay(PaymentMethod.WALLET), staff.getUsername());

        // 現場單：以實收金額（450）為準；含零售商品 300
        WalkInOrder cashWalkIn = walkIn(PaymentMethod.CASH, true, 500, 450,
                item("剪指甲", 200, null), item("洗毛精", 300, shampoo.getId()));
        walkIn(PaymentMethod.WALLET, true, 700, null, item("洗澡", 700, null));
        walkIn(PaymentMethod.CASH, false, 999, null, item("未結帳", 999, null));

        // 顧客轉帳儲值（預收款，不算業績）
        var topUp = topUpService.submit(member.getUsername(), topUpRequest(8000));
        topUpService.confirm(topUp.getId(), "店長");

        // 店用洗劑領用：2 瓶 × 30
        supplyUsageRecordRepository.save(SupplyUsageRecord.builder()
                .supplyId(1L).supplyName("店用洗劑").quantity(2).unitCostSnapshot(30)
                .usedByUsername(staff.getUsername()).usedAt(LocalDateTime.now()).build());

        FinancialReportResponse after = report();

        int revenue = 1200 + 900 + 450 + 700;
        assertThat(after.getTodayRevenueTotal() - before.getTodayRevenueTotal()).isEqualTo(revenue);
        assertThat(after.getTodayRevenueWallet() - before.getTodayRevenueWallet()).isEqualTo(900 + 700);
        assertThat(after.getTodayRevenueNonWallet() - before.getTodayRevenueNonWallet()).isEqualTo(1200 + 450);
        assertThat(after.getTodayOrderCount() - before.getTodayOrderCount()).isEqualTo(4);
        assertThat(after.getTodayTopupCollected() - before.getTodayTopupCollected()).isEqualTo(8000);
        assertThat(after.getTodayRetailRevenue() - before.getTodayRetailRevenue()).isEqualTo(200 + 300);

        assertThat(after.getMonthRevenueTotal() - before.getMonthRevenueTotal()).isEqualTo(revenue);
        assertThat(after.getMonthRevenueWallet() - before.getMonthRevenueWallet()).isEqualTo(900 + 700);
        assertThat(after.getMonthOrderCount() - before.getMonthOrderCount()).isEqualTo(4);
        assertThat(after.getMonthTopupCollected() - before.getMonthTopupCollected()).isEqualTo(8000);
        assertThat(after.getMonthRetailRevenue() - before.getMonthRetailRevenue()).isEqualTo(500);
        assertThat(after.getMonthRetailCostEstimate() - before.getMonthRetailCostEstimate()).isEqualTo(80 + 80);
        assertThat(after.getMonthSupplyCost() - before.getMonthSupplyCost()).isEqualTo(60);
        assertThat(after.getMonthTotalCost() - before.getMonthTotalCost()).isEqualTo(160 + 60);
        assertThat(after.getMonthEstimatedProfit() - before.getMonthEstimatedProfit()).isEqualTo(revenue - 220);

        assertThat(after.getDetails()).hasSize(before.getDetails().size() + 4);
        assertThat(after.getDetails())
                .anySatisfy(line -> {
                    assertThat(line.getCode()).isEqualTo(String.format("AP%03d", cashAppointment.getId()));
                    assertThat(line.getAmount()).isEqualTo(1200);
                })
                .anySatisfy(line -> {
                    assertThat(line.getCode()).isEqualTo("現場單#" + cashWalkIn.getId());
                    assertThat(line.getAmount()).isEqualTo(450);
                });
        assertThat(after.getDetails()).isSortedAccordingTo((a, b) -> b.getPaymentTime().compareTo(a.getPaymentTime()));
    }

    private FinancialReportResponse report() {
        return transactionTemplate.execute(status -> paymentService.getFinancialReport());
    }

    private Appointment appointment(User customer, int total) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName("報表毛孩" + UUID.randomUUID().toString().substring(0, 6))
                .petType("CAT")
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(14, 0))
                .totalAmount(total)
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .finalCheckDone(true)
                .build());
    }

    private GroomingItem groomingItem(int price) {
        String code = "R" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("報表測試項目" + code).price((double) price).build());
    }

    private void checkinItem(Appointment appointment, String name, int price, Long retailProductId) {
        appointmentItemRepository.save(AppointmentItem.builder()
                .appointment(appointment).itemName(name).price(price).retailProductId(retailProductId).build());
    }

    private WalkInOrder walkIn(PaymentMethod method, boolean paid, int total, Integer charged, WalkInOrderItem... items) {
        WalkInOrder order = WalkInOrder.builder()
                .petName("現場毛孩")
                .totalAmount(total)
                .chargedAmount(charged)
                .createdBy("報表測試")
                .paid(paid)
                .paymentMethod(method)
                .paymentTime(paid ? LocalDateTime.now() : null)
                .build();
        for (WalkInOrderItem item : items) {
            item.setOrder(order);
            order.getItems().add(item);
        }
        return walkInOrderRepository.save(order);
    }

    private static WalkInOrderItem item(String name, int price, Long retailProductId) {
        return WalkInOrderItem.builder().itemName(name).price(price).retailProductId(retailProductId).build();
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

    private static TopUpRequestCreate topUpRequest(int amount) {
        TopUpRequestCreate req = new TopUpRequestCreate();
        req.setAmount(amount);
        return req;
    }
}
