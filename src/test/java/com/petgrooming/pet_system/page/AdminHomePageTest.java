package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.GroomingCategoryView;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.support.WebLogin;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理後台：區塊導覽、美容項目分類，存檔後回到原本的區塊。 */
class AdminHomePageTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void homeShowsSectionNavAndGroomingCategories() throws Exception {
        User admin = testData.admin();

        MvcResult result = mvc.perform(get("/admin").cookie(WebLogin.cookie(jwtUtils, admin)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        List<GroomingCategoryView> categories = (List<GroomingCategoryView>) model.get("itemCategories");
        assertThat(categories).extracting(GroomingCategoryView::key)
                .containsExactly("DOG_BATH", "DOG_CARE", "DOG_CUT", "CAT", "SINGLE", "SPECIAL");
        assertThat(categories.stream().filter(c -> c.key().equals("DOG_CUT")).findFirst().orElseThrow().count())
                .isEqualTo(36); // V22 大美容加購
        Map<Long, String> categoryOf = (Map<Long, String>) model.get("itemCategoryOf");
        assertThat(categoryOf).isNotEmpty();

        String html = result.getResponse().getContentAsString();
        // 大美容全手剪 25 分不在常用選項裡，也要選中 25，存檔才不會被改成 0
        int row = html.indexOf(">CUT003<");
        assertThat(row).isPositive();
        String cut003 = html.substring(row, html.indexOf("</tr>", row));
        assertThat(cut003).containsPattern("<option value=\"25\" selected[^>]*>25分</option>");
        assertThat(cut003).doesNotContainPattern("<option value=\"0\" selected");
        assertThat(html).contains("id=\"staff\"", "id=\"add-staff\"", "id=\"items\"", "id=\"users\"",
                "href=\"#items\"", "id=\"catChips\"");
    }

    @Test
    void savingRedirectsBackToTheSection() throws Exception {
        User admin = testData.admin();

        mvc.perform(WebLogin.as(jwtUtils, admin, post("/admin/grooming/delete/999999999")))
                .andExpect(redirectedUrl("/admin#items"));
        mvc.perform(WebLogin.as(jwtUtils, admin, post("/admin/staff/999999999/deactivate")))
                .andExpect(redirectedUrl("/admin#staff"));
    }
}
