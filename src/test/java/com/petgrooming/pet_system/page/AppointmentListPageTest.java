package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.DashboardService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 預約列表（網頁版、手機版）：篩選條件、統計數字（今日營收與首頁一致，以實收金額計）、手機版分組與日期列。
 * 資料庫是共用的，統計數字以「加入已知資料前後的差額」驗證，清單以這次建立的唯一日期／名字篩選。
 */
class AppointmentListPageTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private DashboardService dashboardService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void staffListFiltersByDateStatusPaidAndKeyword() throws Exception {
        User staff = testData.staff();
        User customer = testData.customer();
        LocalDate date = TestData.uniqueFutureDate();
        String tag = tag();
        Appointment confirmed = appointment(customer, date, AppointmentStatus.CONFIRMED, false, tag + "甲");
        Appointment paid = appointment(customer, date, AppointmentStatus.IN_PROGRESS, true, tag + "乙");
        Appointment cancelled = appointment(customer, date, AppointmentStatus.CANCELLED, false, tag + "丙");

        assertThat(adminIds(webList(staff, "dateFrom", date, "dateTo", date)))
                .containsExactlyInAnyOrder(confirmed.getId(), paid.getId(), cancelled.getId());
        assertThat(adminIds(webList(staff, "dateFrom", date, "dateTo", date, "status", "IN_PROGRESS")))
                .containsExactly(paid.getId());
        assertThat(adminIds(webList(staff, "dateFrom", date, "dateTo", date, "paidStatus", "PAID")))
                .containsExactly(paid.getId());
        assertThat(adminIds(webList(staff, "dateFrom", date, "dateTo", date, "paidStatus", "UNPAID")))
                .containsExactlyInAnyOrder(confirmed.getId(), cancelled.getId());
        assertThat(adminIds(webList(staff, "keyword", " " + tag.toUpperCase() + "乙 ")))
                .containsExactly(paid.getId());
        assertThat(adminIds(webList(staff, "keyword", String.valueOf(confirmed.getId()))))
                .contains(confirmed.getId());
    }

    @Test
    void staffSummaryCountsTodayAndPending() throws Exception {
        User staff = testData.staff();
        User customer = testData.customer();
        Map<String, Object> before = webList(staff);

        LocalDate today = LocalDate.now();
        appointment(customer, today, AppointmentStatus.CONFIRMED, false, tag());
        appointment(customer, today, AppointmentStatus.COMPLETED, false, tag());
        appointment(customer, today, AppointmentStatus.CANCELLED, false, tag());
        appointment(customer, today.plusDays(3), AppointmentStatus.PENDING_CONFIRM, false, tag());

        Map<String, Object> after = webList(staff);
        assertThat(delta(before, after, "todayCount")).isEqualTo(2);
        assertThat(delta(before, after, "completedCount")).isEqualTo(1);
        assertThat(delta(before, after, "pendingCount")).isEqualTo(1);
        assertThat(after.get("today")).isEqualTo(today);
    }

    @Test
    void todayRevenueIsTheChargedAmountSameAsTheDashboard() throws Exception {
        User staff = testData.staff();
        Map<String, Object> before = webList(staff);

        walkInOrderRepository.save(WalkInOrder.builder()
                .petName("營收毛孩").totalAmount(500).chargedAmount(450).createdBy("列表測試")
                .paid(true).paymentMethod(PaymentMethod.CASH).paymentTime(LocalDateTime.now()).build());

        Map<String, Object> after = webList(staff);
        assertThat(delta(before, after, "todayRevenue")).isEqualTo(450);
        assertThat(after.get("todayRevenue")).isEqualTo(dashboardService.todayRevenue(LocalDate.now()));
    }

    @Test
    void customerSeesOnlyOwnAppointmentsWithFilters() throws Exception {
        User customer = testData.customer();
        LocalDate date = TestData.uniqueFutureDate();
        Appointment mine = appointment(customer, date, AppointmentStatus.CONFIRMED, false, tag());
        Appointment minePaid = appointment(customer, date, AppointmentStatus.COMPLETED, true, tag());
        appointment(testData.customer(), date, AppointmentStatus.CONFIRMED, false, tag());

        Map<String, Object> model = webList(customer, "dateFrom", date, "dateTo", date);
        assertThat(customerIds(model)).containsExactlyInAnyOrder(mine.getId(), minePaid.getId());
        assertThat(model).doesNotContainKey("todayCount");
        assertThat(customerIds(webList(customer, "paidStatus", "PAID"))).containsExactly(minePaid.getId());
        assertThat(customerIds(webList(customer, "keyword", minePaid.getPetName()))).containsExactly(minePaid.getId());
        assertThat(customerIds(webList(customer, "status", "CONFIRMED"))).containsExactly(mine.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileListPicksByDateKeywordAndFilter() throws Exception {
        User staff = testData.staff();
        User customer = testData.customer();
        LocalDate date = TestData.uniqueFutureDate();
        String tag = tag();
        Appointment late = appointment(customer, date, AppointmentStatus.CONFIRMED, false, tag + "晚", LocalTime.of(15, 0));
        Appointment early = appointment(customer, date, AppointmentStatus.PENDING_CONFIRM, false, tag + "早", LocalTime.of(10, 0));
        Appointment cancelled = appointment(customer, date, AppointmentStatus.CANCELLED, false, tag + "消");

        Map<String, Object> byDate = mobileList(staff, "date", date.toString(), "f", "pending");
        assertThat(byDate.get("total")).isEqualTo(3);
        assertThat(byDate.get("f")).isEqualTo("");
        assertThat(byDate.get("selectedDate")).isEqualTo(date);
        Map<String, List<?>> groups = (Map<String, List<?>>) byDate.get("groups");
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).hasSize(3);

        Map<String, Object> byKeyword = mobileList(staff, "q", "  " + tag + "早 ");
        assertThat(byKeyword.get("total")).isEqualTo(1);
        assertThat(byKeyword.get("q")).isEqualTo(tag + "早");

        Map<String, Object> pending = mobileList(staff, "f", "pending");
        assertThat(pending.get("f")).isEqualTo("pending");
        assertThat(cardIds(pending)).contains(early.getId()).doesNotContain(late.getId(), cancelled.getId());
        assertThat(cardIds(mobileList(staff, "f", "cancelled"))).doesNotContain(late.getId(), early.getId());

        Map<String, Object> upcoming = mobileList(staff);
        assertThat(upcoming.get("f")).isEqualTo("upcoming");
        List<Map<String, Object>> days = (List<Map<String, Object>>) upcoming.get("days");
        assertThat(days).hasSize(7);
        assertThat(days.get(0)).containsEntry("week", "今天").containsEntry("iso", LocalDate.now().toString())
                .containsEntry("on", false);
        assertThat(upcoming).containsKeys("pendingCount", "servingCount");
    }

    @SuppressWarnings("unchecked")
    private static List<Long> cardIds(Map<String, Object> model) {
        return ((Map<String, List<Object>>) model.get("groups")).values().stream()
                .flatMap(List::stream)
                .map(card -> {
                    try {
                        return (Long) card.getClass().getMethod("getId").invoke(card);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> adminIds(Map<String, Object> model) {
        return ((List<AppointmentAdminResponse>) model.get("appointments")).stream()
                .map(AppointmentAdminResponse::getId).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> customerIds(Map<String, Object> model) {
        return ((List<AppointmentResponse>) model.get("appointments")).stream()
                .map(AppointmentResponse::getId).toList();
    }

    private Map<String, Object> webList(User user, Object... params) throws Exception {
        return page(user, "/appointments", params);
    }

    private Map<String, Object> mobileList(User user, Object... params) throws Exception {
        return page(user, "/m/appointments", params);
    }

    private Map<String, Object> page(User user, String path, Object... params) throws Exception {
        var request = get(path).cookie(new Cookie("JWT_TOKEN",
                jwtUtils.generateToken(user.getUsername(), user.getRole().name())));
        for (int i = 0; i < params.length; i += 2) {
            request.param((String) params[i], params[i + 1].toString());
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    private static String tag() {
        return "列表" + UUID.randomUUID().toString().substring(0, 6);
    }

    private Appointment appointment(User customer, LocalDate date, AppointmentStatus status, boolean paid, String petName) {
        return appointment(customer, date, status, paid, petName, LocalTime.NOON);
    }

    private Appointment appointment(User customer, LocalDate date, AppointmentStatus status, boolean paid, String petName,
                                    LocalTime start) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer).petName(petName).petType("CAT")
                .date(date).startTime(start).endTime(start.plusHours(2))
                .totalAmount(1000).paid(paid).status(status)
                .build());
    }
}
