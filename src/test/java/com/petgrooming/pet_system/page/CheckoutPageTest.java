package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 結帳頁：「新增服務項目」選單依體型篩選（狗狗未填體重時提醒）、結帳失敗時回到結帳頁顯示原因。
 */
class CheckoutPageTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PetRepository petRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void dogWithoutWeightShowsReminderAndHidesWeightPackages() throws Exception {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        Pet saved = petRepository.findById(dog.getId()).orElseThrow();
        saved.setWeight(0.0); // 資料庫欄位不可為 null，「未填體重」以 0 表示（例如匯入資料體重填 0）
        petRepository.save(saved);
        Appointment appointment = appointment(customer, dog.getName());

        MvcResult result = checkout(appointment);

        assertThat(result.getModelAndView().getModel().get("weightMissing")).isEqualTo(true);
        assertThat(groomingItems(result)).allSatisfy(i -> assertThat(i.getDogWeightTier()).isNull());
        assertThat(result.getResponse().getContentAsString()).contains("請先填寫體重");
    }

    @Test
    void dogWithWeightHasNoReminder() throws Exception {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer); // 10kg：中小型犬
        Appointment appointment = appointment(customer, dog.getName());

        MvcResult result = checkout(appointment);

        assertThat(result.getModelAndView().getModel().get("weightMissing")).isEqualTo(false);
        assertThat(groomingItems(result))
                .allSatisfy(i -> assertThat(i.getDogWeightTier()).isIn(null, "MEDIUM_SMALL"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("請先填寫體重");
    }

    @Test
    void failedCheckoutShowsTheReasonOnTheCheckoutPage() throws Exception {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        Appointment appointment = appointment(customer, dog.getName());
        appointment.setFinalCheckDone(false); // 還沒核對，結帳會被擋下
        appointmentRepository.save(appointment);
        User staff = testData.staff();

        MvcResult result = mvc.perform(post("/payments/checkout/" + appointment.getId() + "/submit")
                        .param("paymentMethod", "CASH")
                        .cookie(new Cookie("JWT_TOKEN", jwtUtils.generateToken(staff.getUsername(), staff.getRole().name()))))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/checkout"))
                .andReturn();

        assertThat((String) result.getModelAndView().getModel().get("errorMsg")).contains("核對");
        assertThat(result.getResponse().getContentAsString()).contains("核對");
    }

    @SuppressWarnings("unchecked")
    private static List<GroomingItemResponse> groomingItems(MvcResult result) {
        return (List<GroomingItemResponse>) result.getModelAndView().getModel().get("groomingItems");
    }

    private MvcResult checkout(Appointment appointment) throws Exception {
        User staff = testData.staff();
        return mvc.perform(get("/payments/checkout/" + appointment.getId()).cookie(new Cookie("JWT_TOKEN",
                        jwtUtils.generateToken(staff.getUsername(), staff.getRole().name()))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Appointment appointment(User customer, String petName) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName(petName)
                .petType("DOG")
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(14, 0))
                .totalAmount(0)
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .finalCheckDone(true)
                .build());
    }
}
