package com.petgrooming.pet_system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.DogWeightTier;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.support.WebLogin;
import com.petgrooming.pet_system.utils.JwtUtils;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 寵物資料庫直接編輯固定組合：指定項目與固定價，覆蓋原本的組合；至少一項精緻洗、價格不能是負數。
 */
class PetComboEditApiTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private GroomingItemRepository groomingItemRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void staffEditsComboWithCustomPricesAndReplacesPreviousOne() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        GroomingItem bath = item(1000, false);
        GroomingItem hc = item(800, true);
        GroomingItem ad = item(500, true);

        lock(staff, dog, "[{\"groomingItemId\":%d,\"price\":950},{\"groomingItemId\":%d,\"price\":700}]"
                .formatted(bath.getId(), hc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedGroomingItemId").value(bath.getId()))
                .andExpect(jsonPath("$.lockedItems.length()").value(2))
                .andExpect(jsonPath("$.lockedItemPrice").value(1650.0));

        lock(staff, dog, "[{\"groomingItemId\":%d,\"price\":900},{\"groomingItemId\":%d,\"price\":400}]"
                .formatted(bath.getId(), ad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedItems[1].groomingItemId").value(ad.getId()))
                .andExpect(jsonPath("$.lockedItemPrice").value(1300.0));
    }

    @Test
    void comboWithoutPackageOrWithNegativePriceIsRejected() throws Exception {
        User staff = testData.staff();
        PetResponse dog = testData.dog(testData.customer());
        GroomingItem bath = item(1000, false);
        GroomingItem hc = item(800, true);

        lock(staff, dog, "[{\"groomingItemId\":%d,\"price\":700}]".formatted(hc.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("固定組合要包含一項精緻洗套餐"));
        lock(staff, dog, "[{\"groomingItemId\":%d,\"price\":-1}]".formatted(bath.getId()))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions lock(User staff, PetResponse dog, String items)
            throws Exception {
        return mvc.perform(WebLogin.as(jwtUtils, staff, put("/api/admin/pets/{id}/locked-combo", dog.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":" + items + "}"));
    }

    private GroomingItem item(int price, boolean tierAddon) {
        String code = "E" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("組合編輯測試" + code).price((double) price)
                .dogWeightTier(DogWeightTier.MEDIUM_SMALL).tierAddon(tierAddon).build());
    }
}
