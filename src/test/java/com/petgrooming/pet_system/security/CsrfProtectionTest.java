package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.support.WebLogin;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 以 Cookie 登入的寫入請求一律要帶正確的 CSRF token；以 Bearer 登入（LIFF）與讀取請求不檢查。
 */
class CsrfProtectionTest extends IntegrationTestBase {

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
    void cookieFormPostWithoutTokenShowsErrorPage() throws Exception {
        User staff = testData.staff();
        mvc.perform(post("/appointments/slots-manage/closed-date/add")
                        .param("date", TestData.uniqueFutureDate().toString())
                        .cookie(WebLogin.cookie(jwtUtils, staff)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("操作沒有送出")));
    }

    @Test
    void cookieFormPostWithFormFieldPasses() throws Exception {
        User staff = testData.staff();
        mvc.perform(post("/appointments/slots-manage/closed-date/add")
                        .param("date", TestData.uniqueFutureDate().toString())
                        .param("_csrf", WebLogin.CSRF)
                        .cookie(WebLogin.cookie(jwtUtils, staff)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void cookieApiWriteWithoutTokenIs403Json() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        mvc.perform(delete("/api/admin/pets/{id}/locked-item", dog.getId()).cookie(WebLogin.cookie(jwtUtils, staff)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("驗證失效，請重新整理頁面後再試一次"));
    }

    @Test
    void cookieApiWriteWithWrongTokenIs403() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        mvc.perform(put("/api/admin/pets/{id}/weight", dog.getId())
                        .cookie(WebLogin.cookie(jwtUtils, staff))
                        .header("X-CSRF-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieApiWriteWithHeaderPasses() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        mvc.perform(WebLogin.as(jwtUtils, staff, put("/api/admin/pets/{id}/weight", dog.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":8}"))
                .andExpect(status().isOk());
    }

    @Test
    void cookieWithoutCsrfClaimCannotWrite() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        mvc.perform(put("/api/admin/pets/{id}/weight", dog.getId())
                        .cookie(new jakarta.servlet.http.Cookie("JWT_TOKEN",
                                jwtUtils.generateToken(staff.getUsername(), staff.getRole().name())))
                        .header("X-CSRF-Token", "")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bearerWriteNeedsNoToken() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        mvc.perform(delete("/api/pets/{id}", dog.getId())
                        .header("Authorization", "Bearer " + jwtUtils.generateToken(owner.getUsername(), owner.getRole().name())))
                .andExpect(status().isNoContent());
    }

    /** 網頁版與手機版共用的 head 片段都載入 csrf.js，頁面上的表單與 fetch 才會自動帶 token。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/dashboard", "/appointments", "/admin/walk-in-orders", "/m/today", "/m/appointments"})
    void staffPagesLoadCsrfScript(String path) throws Exception {
        mvc.perform(get(path).cookie(WebLogin.cookie(jwtUtils, testData.staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/csrf.js")));
    }

    @Test
    void cookieReadNeedsNoToken() throws Exception {
        mvc.perform(get("/dashboard").cookie(WebLogin.cookie(jwtUtils, testData.staff())))
                .andExpect(status().isOk());
    }
}
