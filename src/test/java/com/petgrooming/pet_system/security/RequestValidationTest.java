package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 請求內容驗證：格式錯誤時回 400，回應格式維持 {"message": "..."}，前端不需修改。
 */
class RequestValidationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestData testData;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void lineBindWithoutCodeIsRejected() throws Exception {
        mvc.perform(post("/api/line/bind").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請提供驗證碼"));
    }

    @Test
    void memberLookupWithoutPhoneIsRejected() throws Exception {
        mvc.perform(post("/api/line/member-lookup").header("Authorization", bearer(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請輸入 09 開頭的 10 碼手機號碼"));
    }

    @Test
    void memberLookupWithMalformedPhoneIsRejected() throws Exception {
        mvc.perform(post("/api/line/member-lookup").header("Authorization", bearer(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請輸入 09 開頭的 10 碼手機號碼"));
    }

    @Test
    void claimWithoutPhoneIsRejected() throws Exception {
        mvc.perform(post("/api/line/claim-by-phone").header("Authorization", bearer(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verification\":\"小白\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請輸入電話號碼"));
    }

    @Test
    void petWeightMustBePositive() throws Exception {
        PetResponse dog = testData.dog(testData.customer());

        mvc.perform(put("/api/admin/pets/{id}/weight", dog.getId()).header("Authorization", bearer(UserRole.STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請提供有效的體重"));
    }

    @Test
    void validPetWeightIsSaved() throws Exception {
        PetResponse dog = testData.dog(testData.customer());

        mvc.perform(put("/api/admin/pets/{id}/weight", dog.getId()).header("Authorization", bearer(UserRole.STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(12.5));
    }

    @Test
    void lockingGroomingItemRequiresItemId() throws Exception {
        PetResponse dog = testData.dog(testData.customer());

        mvc.perform(put("/api/admin/pets/{id}/lock-grooming-item", dog.getId()).header("Authorization", bearer(UserRole.STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請提供 groomingItemId"));
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtils.generateToken("validation-test-" + role.name().toLowerCase() + "@test.local", role.name());
    }
}
