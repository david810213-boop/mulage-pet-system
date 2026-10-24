package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.MobileAppointmentCard;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 員工頁面冒煙測試（網頁版代客預約、時段管理；手機版預約明細、現場單列表與開單）：
 * 頁面能正常渲染、畫面需要的資料都有、表單送出後導向正確。
 */
class StaffPagesSmokeTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private WalkInOrderService walkInOrderService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void staffAssistedBookingPages() throws Exception {
        User staff = testData.staff();
        User member = testData.customer();
        testData.dog(member);

        page(staff, get("/appointments/select-member"), "appointments/select-member");
        Map<String, Object> form = page(staff, get("/appointments/new").param("forUsername", member.getUsername()),
                "appointments/form");
        assertThat(form).containsEntry("forUsername", member.getUsername())
                .containsEntry("targetMemberName", member.getName())
                .containsKeys("myPets", "appointmentRequest", "groomingItems", "companySignatureImage");
        assertThat((List<?>) form.get("myPets")).hasSize(1);

        mvc.perform(as(staff, get("/appointments/new").param("forUsername", "nobody-" + UUID.randomUUID())))
                .andExpect(redirectedUrl("/appointments/select-member"));
        Map<String, Object> own = page(member, get("/appointments/new"), "appointments/form");
        assertThat(own.get("forUsername")).isNull();

        Map<String, Object> slots = page(staff, get("/appointments/slots").param("date", testData.openFutureDate().toString()),
                "appointments/slots");
        assertThat(slots).containsKey("slots");
    }

    @Test
    void slotManagementPagesAndClosedDates() throws Exception {
        User staff = testData.staff();
        LocalDate date = TestData.uniqueFutureDate();

        Map<String, Object> model = page(staff, get("/appointments/slots-manage").param("date", date.toString()),
                "appointments/slots-manage");
        assertThat(model).containsEntry("date", date).containsEntry("isClosedToday", false)
                .containsKeys("slots", "upcomingClosedDates", "weeklyClosureSetting", "templateSlots");

        mvc.perform(as(staff, post("/appointments/slots-manage/closed-date/add")
                        .param("date", date.toString()).param("reason", "員工旅遊")))
                .andExpect(redirectedUrl("/appointments/slots-manage?date=" + date))
                .andExpect(flash().attribute("successMsg", date + " 已設定為公休日"));
        assertThat(page(staff, get("/appointments/slots-manage").param("date", date.toString()), "appointments/slots-manage"))
                .containsEntry("isClosedToday", true);

        mvc.perform(as(staff, post("/appointments/slots-manage/closed-date/remove").param("date", date.toString())))
                .andExpect(redirectedUrl("/appointments/slots-manage?date=" + date))
                .andExpect(flash().attribute("successMsg", date + " 已取消公休設定"));
    }

    @Test
    void mobileAppointmentDetail() throws Exception {
        User staff = testData.staff();
        User customer = testData.customer();
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .user(customer).petName("明細毛孩").petType("DOG")
                .date(LocalDate.now()).startTime(LocalTime.of(15, 30)).endTime(LocalTime.of(17, 30))
                .totalAmount(1000).paid(false).status(AppointmentStatus.PENDING_CONFIRM).build());

        Map<String, Object> model = page(staff, get("/m/appointments/" + appointment.getId()), "m/appointment-detail");

        MobileAppointmentCard card = (MobileAppointmentCard) model.get("card");
        assertThat(card.getId()).isEqualTo(appointment.getId());
        assertThat(card.getStage()).isEqualTo("confirm");
        assertThat(card.getStageLabel()).isEqualTo("待確認");
        assertThat(card.getState()).isEqualTo("pending");
        assertThat(card.getTime()).isEqualTo("15:30");
        assertThat(card.getPetInitial()).isEqualTo("明");
        assertThat(card.getSpecies()).isEqualTo("dog");
        assertThat(card.getSpeciesLabel()).isEqualTo("狗狗");
        assertThat(card.getDateLabel()).startsWith(LocalDate.now().getMonthValue() + "/" + LocalDate.now().getDayOfMonth() + " 週");
        assertThat(model).containsEntry("step", 0).containsEntry("canCheckout", true).containsEntry("canCancel", true);

        mvc.perform(as(staff, get("/m/appointments/999999999"))).andExpect(redirectedUrl("/m/appointments"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileWalkInListSearchAndDetail() throws Exception {
        User staff = testData.staff();
        User member = testData.customer();
        var dog = testData.dog(member);
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(member.getUsername());
        req.setPetName(dog.getName());
        req.setItems(new ArrayList<>());
        req.setRetailItems(new ArrayList<>(List.of(retail())));
        Long orderId = walkInOrderService.create(req, staff.getUsername()).getId();

        Map<String, Object> list = page(staff, get("/m/walk-in"), "m/walk-in");
        assertThat((List<Map<String, Object>>) list.get("openOrders"))
                .anySatisfy(row -> assertThat(row.get("id")).isEqualTo(orderId));

        Map<String, Object> pick = page(staff, get("/m/walk-in/new").param("q", member.getName()), "m/walk-in-new");
        assertThat(pick).containsEntry("step", "pick").containsEntry("q", member.getName());
        assertThat((List<Map<String, Object>>) pick.get("results")).isNotEmpty();

        Map<String, Object> form = page(staff, get("/m/walk-in/new")
                .param("member", member.getUsername()).param("pet", dog.getName()), "m/walk-in-new");
        assertThat(form).containsEntry("step", "form").containsEntry("memberName", member.getName())
                .containsKeys("mainItems", "otherItems", "suggestedCode", "staffOptions", "retailProducts", "backQuery");

        Map<String, Object> detail = page(staff, get("/m/walk-in/" + orderId), "m/walk-in-detail");
        assertThat(detail).containsKeys("o", "row", "lines", "stage", "staffOptions").containsEntry("petSpecies", "dog");
        assertThat(((Map<String, Object>) detail.get("row")).get("id")).isEqualTo(orderId);
    }

    private WalkInOrderCreateRequest.RetailItem retail() {
        WalkInOrderCreateRequest.RetailItem item = new WalkInOrderCreateRequest.RetailItem();
        item.setRetailProductId(testRetailProductId());
        item.setQuantity(1);
        return item;
    }

    @Autowired private com.petgrooming.pet_system.service.RetailProductService retailProductService;

    private Long testRetailProductId() {
        return retailProductService.create("冒煙測試商品-" + UUID.randomUUID().toString().substring(0, 6), 100, 5, null, 50).getId();
    }

    private Map<String, Object> page(User user, MockHttpServletRequestBuilder request, String viewName) throws Exception {
        return mvc.perform(as(user, request))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andReturn().getModelAndView().getModel();
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("JWT_TOKEN", jwtUtils.generateToken(user.getUsername(), user.getRole().name())));
    }
}
