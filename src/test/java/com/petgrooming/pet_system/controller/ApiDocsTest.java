package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 開發環境開啟 API 文件時，能產生完整的 OpenAPI 文件（只列 /api/**）。 */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class ApiDocsTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtUtils jwtUtils;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void openApiDocumentListsApiPaths() throws Exception {
        String token = "Bearer " + jwtUtils.generateToken("docs-test@test.local", UserRole.STAFF.name());
        mvc.perform(get("/v3/api-docs").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("慕沐村 Mulage Pet API"))
                .andExpect(jsonPath("$.paths['/api/top-ups']").exists())
                .andExpect(jsonPath("$.paths['/api/me/appointments']").exists())
                .andExpect(content().string(containsString("儲值金額，單筆最低 5,000")));
    }
}
