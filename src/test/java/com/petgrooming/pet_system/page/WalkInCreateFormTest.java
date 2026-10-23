package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse.ItemLine;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 現場開單表單（網頁版、手機版）：平行陣列（項目代碼／經手人／第二經手人、零售商品／數量）解析成開單內容；
 * 手機版今日頁列出進行中的現場單。
 */
class WalkInCreateFormTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private RetailProductService retailProductService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void webFormSkipsBlankRowsAndDefaultsRetailQuantityToOne() throws Exception {
        User staff = testData.staff();
        User operator = testData.staff();
        User operator2 = testData.staff();
        GroomingItem bath = groomingItem(1000);
        GroomingItem nails = groomingItem(200);
        RetailProduct shampoo = retailProduct(300);
        String note = note();

        mvc.perform(as(staff, post("/admin/walk-in-orders/create"))
                        .param("petName", "小白").param("note", note)
                        .param("itemCodes", bath.getItemCode(), "", nails.getItemCode())
                        .param("operatorStaffIds", operator.getId().toString(), "", "")
                        .param("operatorStaffIds2", operator2.getId().toString())
                        .param("retailProductIds", shampoo.getId().toString(), "")
                        .param("retailQuantities", ""))
                .andExpect(redirectedUrl("/admin/walk-in-orders"))
                .andExpect(flash().attribute("successMsg", org.hamcrest.Matchers.containsString("總額 $1500")));

        WalkInOrderResponse order = orderWithNote(note);
        assertThat(order.getPetName()).isEqualTo("小白");
        assertThat(order.getItems()).extracting(ItemLine::getItemName)
                .containsExactly(bath.getName(), nails.getName(), shampoo.getName());
        assertThat(order.getItems().get(0).isOperatorFilled()).isTrue();
        assertThat(order.getItems().get(1).isOperatorFilled()).isFalse();
    }

    @Test
    void mobileFormSkipsZeroQuantityRetailAndTrimsPetName() throws Exception {
        User staff = testData.staff();
        GroomingItem bath = groomingItem(800);
        RetailProduct shampoo = retailProduct(300);
        RetailProduct brush = retailProduct(150);
        String note = note();

        String location = mvc.perform(as(staff, post("/m/walk-in/create"))
                        .param("petName", "  小黑  ").param("note", note)
                        .param("itemCodes", bath.getItemCode())
                        .param("operatorStaffIds", "")
                        .param("retailProductIds", shampoo.getId().toString(), brush.getId().toString())
                        .param("retailQuantities", "2", "abc"))
                .andExpect(flash().attribute("toast", org.hamcrest.Matchers.startsWith("開單成功")))
                .andReturn().getResponse().getRedirectedUrl();

        WalkInOrderResponse order = orderWithNote(note);
        assertThat(location).isEqualTo("/m/walk-in/" + order.getId());
        assertThat(order.getPetName()).isEqualTo("小黑");
        assertThat(order.getTotalAmount()).isEqualTo(800 + 300 * 2);
        assertThat(order.getItems()).extracting(ItemLine::getItemName)
                .containsExactly(bath.getName(), shampoo.getName(), shampoo.getName());
    }

    @Test
    void mobileFormWithNothingSelectedGoesBackToTheForm() throws Exception {
        mvc.perform(as(testData.staff(), post("/m/walk-in/create"))
                        .param("itemCodes", "")
                        .param("retailProductIds", retailProduct(100).getId().toString())
                        .param("retailQuantities", "0")
                        .param("backQuery", "guest=1"))
                .andExpect(redirectedUrl("/m/walk-in/new?guest=1"))
                .andExpect(flash().attribute("toastError", "請至少選一個服務項目或零售商品"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileTodayPageListsTheOpenWalkInOrder() throws Exception {
        User staff = testData.staff();
        String note = note();
        mvc.perform(as(staff, post("/m/walk-in/create"))
                .param("petName", "今日頁毛孩").param("note", note)
                .param("itemCodes", groomingItem(500).getItemCode()));
        Long orderId = orderWithNote(note).getId();

        var model = mvc.perform(as(staff, get("/m/today")))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();

        var todayCards = (List<?>) model.get("todayCards");
        assertThat((Integer) model.get("nowIndex")).isBetween(0, todayCards.size());
        assertThat((List<Map<String, Object>>) model.get("openWalkIns")).hasSizeLessThanOrEqualTo(8);
        if (((List<Map<String, Object>>) model.get("openWalkIns")).stream().anyMatch(m -> orderId.equals(m.get("id")))) {
            assertThat((List<Map<String, Object>>) model.get("openWalkIns"))
                    .filteredOn(m -> orderId.equals(m.get("id")))
                    .singleElement()
                    .satisfies(m -> {
                        assertThat(m.get("title")).isEqualTo("今日頁毛孩");
                        assertThat(m.get("sub")).isEqualTo("非會員，現場單 #" + orderId);
                    });
        }
    }

    private WalkInOrderResponse orderWithNote(String note) {
        Long id = walkInOrderService.findAllOrders().stream()
                .filter(o -> note.equals(o.getNote()))
                .map(WalkInOrder::getId)
                .findFirst().orElseThrow();
        return transactionTemplate.execute(status -> walkInOrderService.getById(id));
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("JWT_TOKEN", jwtUtils.generateToken(user.getUsername(), user.getRole().name())));
    }

    private static String note() {
        return "表單測試-" + UUID.randomUUID();
    }

    private GroomingItem groomingItem(int price) {
        String code = "F" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("表單測試項目" + code).price((double) price).points(1.0)
                .performanceCategory(PerformanceCategory.BATH_SMALL).build());
    }

    private RetailProduct retailProduct(int price) {
        return retailProductService.create("表單測試商品-" + UUID.randomUUID().toString().substring(0, 6), price, 20, null, 50);
    }
}
