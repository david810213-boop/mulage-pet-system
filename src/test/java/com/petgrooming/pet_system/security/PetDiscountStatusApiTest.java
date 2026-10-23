package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.service.CatFirstVisitDiscountService;
import com.petgrooming.pet_system.service.CatRewashDiscountService;
import com.petgrooming.pet_system.service.DogFirstVisitDiscountService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIFF 預約頁的寵物折扣資格（GET /api/pets/{petId}/discount-status）：
 * 首次體驗優惠、貓咪 90 天定期養護禮遇（以選定的預約日期為基準）、會員折扣率與權限。
 */
class PetDiscountStatusApiTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void newDogGetsFirstVisitOffer() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);

        discountStatus(owner, dog, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.petType").value("DOG"))
                .andExpect(jsonPath("$.firstVisitEligible").value(true))
                .andExpect(jsonPath("$.rewashEligible").value(false))
                .andExpect(jsonPath("$.lastBathDaysAgo").value(nullValue()))
                .andExpect(jsonPath("$.memberDiscountRate").value(1.0))
                .andExpect(jsonPath("$.specialDiscountLabel").value("首次體驗優惠"))
                .andExpect(jsonPath("$.specialDiscountRate").value(DogFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE))
                .andExpect(jsonPath("$.specialDiscountCategories", contains("BATH_SMALL", "BATH_LARGE")));
    }

    @Test
    void newCatGetsFirstVisitOffer() throws Exception {
        User owner = testData.customer();
        PetResponse cat = testData.cat(owner);

        discountStatus(owner, cat, null)
                .andExpect(jsonPath("$.firstVisitEligible").value(true))
                .andExpect(jsonPath("$.specialDiscountRate").value(CatFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE))
                .andExpect(jsonPath("$.specialDiscountCategories", contains("BATH_CAT_S", "BATH_CAT_L")));
    }

    @Test
    void catBathedRecentlyGetsRewashOfferDependingOnTheChosenDate() throws Exception {
        User owner = testData.customer();
        PetResponse cat = testData.cat(owner);
        paidCatBath(owner, cat.getName(), LocalDate.now().minusDays(30));

        discountStatus(owner, cat, null)
                .andExpect(jsonPath("$.firstVisitEligible").value(false))
                .andExpect(jsonPath("$.rewashEligible").value(true))
                .andExpect(jsonPath("$.lastBathDaysAgo").value(30))
                .andExpect(jsonPath("$.specialDiscountLabel").value("定期養護禮遇"))
                .andExpect(jsonPath("$.specialDiscountRate").value(CatRewashDiscountService.REWASH_DISCOUNT_RATE));

        discountStatus(owner, cat, LocalDate.now().plusDays(100).toString())
                .andExpect(jsonPath("$.rewashEligible").value(false))
                .andExpect(jsonPath("$.lastBathDaysAgo").value(130))
                .andExpect(jsonPath("$.specialDiscountLabel").value(nullValue()))
                .andExpect(jsonPath("$.specialDiscountRate").value(nullValue()))
                .andExpect(jsonPath("$.specialDiscountCategories").isEmpty());

        discountStatus(owner, cat, "不是日期") // 格式錯誤就退回用今天
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastBathDaysAgo").value(30));
    }

    @Test
    void otherCustomersPetIsForbidden() throws Exception {
        PetResponse dog = testData.dog(testData.customer());

        discountStatus(testData.customer(), dog, null).andExpect(status().isForbidden());
    }

    private ResultActions discountStatus(User user, PetResponse pet, String asOfDate) throws Exception {
        var request = get("/api/pets/" + pet.getId() + "/discount-status")
                .header("Authorization", "Bearer " + jwtUtils.generateToken(user.getUsername(), user.getRole().name()));
        if (asOfDate != null) request.param("asOfDate", asOfDate);
        return mvc.perform(request);
    }

    private void paidCatBath(User owner, String petName, LocalDate date) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        GroomingItem bath = groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("折扣狀態測試洗澡" + code).price(1000.0)
                .performanceCategory(PerformanceCategory.BATH_CAT_S).build());
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("CAT")
                .date(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .totalAmount(1000).paid(true).status(AppointmentStatus.COMPLETED)
                .selectedItems(new ArrayList<>(List.of(bath)))
                .build());
    }
}
