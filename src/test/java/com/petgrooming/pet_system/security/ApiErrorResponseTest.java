package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統一錯誤回應格式：狀態碼、code、message（與 detail 相同）。前端一律讀 message，新程式可依 code 判斷。
 */
class ApiErrorResponseTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestData testData;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentService appointmentService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void missingResourceIs404WithCode() throws Exception {
        mvc.perform(put("/api/admin/pets/{id}/weight", 999_999_999L).header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("找不到寵物 #999999999"))
                .andExpect(jsonPath("$.detail").value("找不到寵物 #999999999"));
    }

    @Test
    void cancellingSomeoneElsesAppointmentIs403() throws Exception {
        User owner = testData.customer();
        User stranger = testData.customer();
        PetResponse dog = testData.dog(owner);
        AppointmentResponse booked = appointmentService.book(
                bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.NOON), owner.getUsername());

        mvc.perform(post("/api/appointments/{id}/cancel", booked.getId()).header("Authorization", tokenFor(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("權限不足：只能取消自己的預約"));
    }

    /** LIFF「我的資料」頁依 409 判斷電話屬於未認領的既有會員，並導向新客報到，此狀態碼不可改變。 */
    @Test
    void phoneOfUnclaimedMemberOnProfileUpdateIs409() throws Exception {
        String phone = "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        User imported = testData.customer();
        imported.setPhone(phone);
        userRepository.save(imported);
        User lineUser = testData.customer();
        lineUser.setLineUserId("U" + UUID.randomUUID().toString().replace("-", ""));
        userRepository.save(lineUser);

        mvc.perform(put("/api/users/me").header("Authorization", tokenFor(lineUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"王小明","phone":"%s","mailingAddress":"新北市板橋區",
                                 "emergencyContactName":"王媽媽","emergencyContactPhone":"0911111111",
                                 "emergencyContactRelation":"母親"}""".formatted(phone)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void validationErrorsListEveryField() throws Exception {
        mvc.perform(post("/api/line/bind").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("請提供驗證碼"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void malformedJsonIs400WithFriendlyMessage() throws Exception {
        mvc.perform(put("/api/admin/pets/{id}/weight", 1L).header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("請求內容不正確，請確認後再試一次"));
    }

    @Test
    void wrongPathVariableTypeIs400() throws Exception {
        mvc.perform(put("/api/admin/pets/not-a-number/weight").header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private String staffToken() {
        return "Bearer " + jwtUtils.generateToken("error-test-staff@test.local", UserRole.STAFF.name());
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getUsername(), user.getRole().name());
    }
}
