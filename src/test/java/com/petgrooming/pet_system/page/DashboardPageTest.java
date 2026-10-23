package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.TopUpRequestCreate;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首頁 Dashboard 顯示的統計數字與清單。資料庫是共用的，數字以「加入已知資料前後的差額」驗證。
 */
class DashboardPageTest extends IntegrationTestBase {

    private static final List<String> STAFF_ATTRIBUTES = List.of(
            "user", "appointments", "todayCount", "todayRevenue", "pendingConfirmCount", "pendingTopupCount",
            "memberCount", "pendingAppointments", "pendingTopups", "recentCustomers", "walletsByUsername",
            "retailStockPreview", "retailProductCount", "lowStockCount", "today");

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private TopUpService topUpService;
    @Autowired private RetailProductService retailProductService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void staffDashboardShowsTodayFiguresPendingWorkAndStock() throws Exception {
        User staff = testData.staff();
        Map<String, Object> before = dashboard(staff);

        User customer = testData.customer();
        appointment(customer, LocalDate.now(), AppointmentStatus.CONFIRMED, false, 0);
        appointment(customer, LocalDate.now().plusDays(1), AppointmentStatus.PENDING_CONFIRM, false, 0);
        appointment(customer, LocalDate.now().minusDays(3), AppointmentStatus.PENDING_CONFIRM, false, 0); // 過期待確認不列入
        Appointment paid = appointment(customer, LocalDate.now().plusDays(2), AppointmentStatus.IN_PROGRESS, true, 1000);
        paymentService.checkout(paid.getId(), cash(), staff.getUsername());
        walkInOrderRepository.save(WalkInOrder.builder().petName("現場").totalAmount(500).chargedAmount(450)
                .paid(true).paymentMethod(PaymentMethod.CASH).paymentTime(LocalDateTime.now()).build());
        TopUpRequestCreate topUp = new TopUpRequestCreate();
        topUp.setAmount(5000);
        topUpService.submit(customer.getUsername(), topUp);
        RetailProduct lowStock = retailProductService.create("首頁測試商品-" + UUID.randomUUID().toString().substring(0, 6),
                100, 0, null, 50);

        Map<String, Object> after = dashboard(staff);

        assertThat(after.keySet()).containsAll(STAFF_ATTRIBUTES);
        assertThat(delta(before, after, "todayCount")).isEqualTo(1);
        assertThat(delta(before, after, "pendingConfirmCount")).isEqualTo(1);
        assertThat(delta(before, after, "todayRevenue")).isEqualTo(1000 + 500); // 現場單以帳面金額計
        assertThat(delta(before, after, "pendingTopupCount")).isEqualTo(1);
        assertThat(delta(before, after, "memberCount")).isEqualTo(1);
        assertThat(delta(before, after, "retailProductCount")).isEqualTo(1);
        assertThat(delta(before, after, "lowStockCount")).isEqualTo(1);
        assertThat(after.get("today")).isEqualTo(LocalDate.now());

        @SuppressWarnings("unchecked")
        List<AppointmentAdminResponse> pending = (List<AppointmentAdminResponse>) after.get("pendingAppointments");
        assertThat(pending).hasSizeLessThanOrEqualTo(8)
                .allMatch(a -> !a.getDate().isBefore(LocalDate.now()))
                .isSortedAccordingTo(Comparator.comparing(AppointmentAdminResponse::getDate)
                        .thenComparing(AppointmentAdminResponse::getStartTime));
        assertThat(pending).anyMatch(a -> a.getDate().equals(LocalDate.now().plusDays(1)));

        @SuppressWarnings("unchecked")
        List<User> recent = (List<User>) after.get("recentCustomers");
        @SuppressWarnings("unchecked")
        Map<String, WalletResponse> wallets = (Map<String, WalletResponse>) after.get("walletsByUsername");
        assertThat(recent).hasSizeLessThanOrEqualTo(8);
        assertThat(wallets.keySet()).containsExactlyInAnyOrderElementsOf(recent.stream().map(User::getUsername).toList());

        @SuppressWarnings("unchecked")
        List<RetailProduct> stock = (List<RetailProduct>) after.get("retailStockPreview");
        assertThat(stock).hasSizeLessThanOrEqualTo(8)
                .isSortedAccordingTo(Comparator.comparingInt(RetailProduct::getStockQuantity));
        assertThat(stock).anyMatch(p -> p.getId().equals(lowStock.getId()));
    }

    @Test
    void customerDashboardShowsOwnAppointmentsAndPets() throws Exception {
        User customer = testData.customer();
        testData.dog(customer);

        Map<String, Object> model = dashboard(customer);

        assertThat(model).containsKeys("user", "appointments", "pets").doesNotContainKey("todayRevenue");
        assertThat((List<?>) model.get("pets")).hasSize(1);
    }

    private Map<String, Object> dashboard(User user) throws Exception {
        return mvc.perform(get("/dashboard").cookie(new Cookie("JWT_TOKEN",
                        jwtUtils.generateToken(user.getUsername(), user.getRole().name()))))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    private Appointment appointment(User customer, LocalDate date, AppointmentStatus status, boolean readyToPay, int price) {
        Appointment.AppointmentBuilder builder = Appointment.builder()
                .user(customer).petName("首頁毛孩" + UUID.randomUUID().toString().substring(0, 6)).petType("CAT")
                .date(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .totalAmount(price).paid(false).status(status).finalCheckDone(readyToPay);
        if (price > 0) {
            String code = "H" + UUID.randomUUID().toString().substring(0, 8);
            GroomingItem item = groomingItemRepository.save(GroomingItem.builder()
                    .itemCode(code).name("首頁測試項目" + code).price((double) price).build());
            builder.selectedItems(new ArrayList<>(List.of(item)));
        }
        return appointmentRepository.save(builder.build());
    }

    private static CheckoutRequest cash() {
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(PaymentMethod.CASH);
        return req;
    }
}
