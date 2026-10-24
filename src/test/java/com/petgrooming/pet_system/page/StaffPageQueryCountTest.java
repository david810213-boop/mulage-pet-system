package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.QueryCounter;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 員工常用頁面的 SQL 查詢次數：加入更多歷史預約後，查詢次數不應跟著變多（N+1 查詢）；
 * 首頁、手機版今日頁、預約明細只載入需要的預約，不撈全部歷史。
 */
class StaffPageQueryCountTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private QueryCounter queryCounter;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void queryCountDoesNotGrowWithHistory() throws Exception {
        User staff = testData.staff();
        Long detailId = history(1).get(0);
        String[] pages = {"/dashboard", "/m/today", "/m/appointments/" + detailId, "/appointments", "/m/appointments",
                "/payments", "/admin/walk-in-orders"};
        Map<String, QueryCounter.Usage> before = new LinkedHashMap<>();
        for (String page : pages) before.put(page, measure(staff, page));
        history(15);
        for (String page : pages) {
            QueryCounter.Usage after = measure(staff, page);
            System.out.println("[量測] " + page + "：加入 15 筆歷史預約前 " + before.get(page).statements() + " 條 SQL／載入 "
                    + before.get(page).entitiesLoaded() + " 筆資料，之後 " + after.statements() + " 條／" + after.entitiesLoaded() + " 筆");
            assertThat(after.statements()).as(page + " 的 SQL 條數").isLessThanOrEqualTo(before.get(page).statements());
        }
    }

    @Test
    void dashboardTodayAndDetailDoNotLoadHistory() throws Exception {
        User staff = testData.staff();
        Long detailId = history(1).get(0);
        for (String page : new String[]{"/dashboard", "/m/today", "/m/appointments/" + detailId}) {
            measure(staff, page); // 先跑一次，排除第一次載入的快取差異
            QueryCounter.Usage before = measure(staff, page);
            history(10);
            QueryCounter.Usage after = measure(staff, page);
            assertThat(after.entitiesLoaded()).as(page + " 載入的資料筆數").isLessThanOrEqualTo(before.entitiesLoaded());
        }
    }

    private QueryCounter.Usage measure(User staff, String url) throws Exception {
        return queryCounter.measure(() -> mvc.perform(get(url).cookie(new Cookie("JWT_TOKEN",
                jwtUtils.generateToken(staff.getUsername(), staff.getRole().name())))).andExpect(status().isOk()));
    }

    /** 建立已到店開單、指派經手人的歷史預約（不同顧客、不同經手人）。 */
    private List<Long> history(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            User customer = testData.customer();
            User groomer = testData.staff();
            String code = "Q" + UUID.randomUUID().toString().substring(0, 8);
            GroomingItem item = groomingItemRepository.save(GroomingItem.builder()
                    .itemCode(code).name("量測項目" + code).price(1000.0).points(1.0)
                    .performanceCategory(PerformanceCategory.BATH_SMALL).build());
            Appointment a = appointmentRepository.save(Appointment.builder()
                    .user(customer).staff(groomer).petName("量測毛孩" + i).petType("DOG")
                    .date(LocalDate.now().minusDays(i + 1)).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                    .selectedItems(new ArrayList<>(List.of(item))).totalAmount(1000).paid(true)
                    .status(AppointmentStatus.COMPLETED).checkinOrderConfirmed(true).finalCheckDone(true)
                    .serviceEndedDone(true).serviceEndedStaff(groomer).finalCheckStaff(groomer).build());
            appointmentItemRepository.save(AppointmentItem.builder().appointment(a).groomingItemId(item.getId())
                    .itemName(item.getName()).price(1000).points(1.0).performanceCategory(PerformanceCategory.BATH_SMALL)
                    .operatorStaff(groomer).build());
            ids.add(a.getId());
        }
        return ids;
    }
}
