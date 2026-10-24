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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 員工常用頁面的 SQL 查詢次數：加入更多歷史預約後，查詢次數不應跟著變多（N+1 查詢）。
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
    void measure() throws Exception {
        User staff = testData.staff();
        Long detailId = history(1).get(0);
        Map<String, long[]> counts = new LinkedHashMap<>();
        String[] pages = {"/dashboard", "/appointments", "/m/today", "/m/appointments", "/m/appointments/" + detailId,
                "/payments", "/admin/walk-in-orders"};
        for (String page : pages) counts.put(page, new long[2]);
        for (String page : pages) counts.get(page)[0] = count(staff, page);
        history(15);
        for (String page : pages) counts.get(page)[1] = count(staff, page);
        counts.forEach((page, c) -> System.out.println("[量測] " + page + "：加入 15 筆歷史預約前 " + c[0] + " 條 SQL，之後 " + c[1] + " 條"));
    }

    private long count(User staff, String url) throws Exception {
        return queryCounter.count(() -> mvc.perform(get(url).cookie(new Cookie("JWT_TOKEN",
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
