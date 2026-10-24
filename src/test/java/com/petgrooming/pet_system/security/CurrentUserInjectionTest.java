package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.model.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** @CurrentUser 注入：REST 端點取得登入帳號、網頁版頁面取得登入者實體。 */
class CurrentUserInjectionTest extends IntegrationTestBase {

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
    void restEndpointReceivesTheLoggedInUsername() throws Exception {
        User customer = testData.customer();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(customer.getUsername()));
    }

    @Test
    void staffPageReceivesTheLoggedInUser() throws Exception {
        User staff = testData.staff();

        mvc.perform(get("/dashboard").cookie(new Cookie("JWT_TOKEN", token(staff))))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("user"));
    }

    private String token(User user) {
        return jwtUtils.generateToken(user.getUsername(), user.getRole().name());
    }
}
