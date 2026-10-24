package com.petgrooming.pet_system.controller;

import com.jayway.jsonpath.JsonPath;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新路徑與舊路徑回傳相同內容。舊路徑保留一個版本週期給可能被快取的 LIFF 頁面，
 * 確認 log 中不再出現「已淘汰的 API」後再移除舊路徑與本測試的舊路徑比對。
 */
class ApiPathMigrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestData testData;

    @Autowired
    private AppointmentService appointmentService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void customerMePathsMatchOldPaths() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        appointmentService.book(bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.NOON),
                owner.getUsername());
        String token = tokenFor(owner);

        assertSameBody(get("/api/me/pets").header("Authorization", token),
                get("/api/pets/my").header("Authorization", token));
        assertSameBody(get("/api/me/appointments").header("Authorization", token),
                get("/api/appointments/my").header("Authorization", token));
        assertSameBody(get("/api/me/transactions").header("Authorization", token),
                get("/api/payments/my").header("Authorization", token));
        assertSameBody(get("/api/me/top-ups").header("Authorization", token),
                get("/api/topup/my").header("Authorization", token));
    }

    @Test
    void appointmentDetailWithoutSuffix() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        AppointmentResponse booked = appointmentService.book(
                bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.NOON), owner.getUsername());
        String token = tokenFor(owner);

        assertSameBody(get("/api/appointments/{id}", booked.getId()).header("Authorization", token),
                get("/api/appointments/{id}/detail", booked.getId()).header("Authorization", token));
    }

    @Test
    void adminMemberPathsUseUserId() throws Exception {
        User owner = testData.customer();
        testData.dog(owner);
        String staff = staffToken();

        String byId = assertSameBody(get("/api/admin/members/{id}/pets", owner.getId()).header("Authorization", staff),
                get("/api/admin/members/{username}/pets", owner.getUsername()).header("Authorization", staff));
        assertThat(byId).contains(owner.getName());

        mvc.perform(put("/api/admin/members/{id}/note", owner.getId()).header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adminNote\":\"怕吹風機\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.adminNote").value("怕吹風機"));
        assertSameBody(get("/api/admin/members/{id}/note", owner.getId()).header("Authorization", staff),
                get("/api/admin/members/{username}/note", owner.getUsername()).header("Authorization", staff));
    }

    @Test
    void adminWalletPathsUseUserId() throws Exception {
        User owner = testData.customer();
        String staff = staffToken();
        mvc.perform(get("/api/admin/wallet/{username}", owner.getUsername()).header("Authorization", staff))
                .andExpect(status().isOk());

        assertSameBody(get("/api/admin/wallets/{id}", owner.getId()).header("Authorization", staff),
                get("/api/admin/wallet/{username}", owner.getUsername()).header("Authorization", staff));
        assertSameBody(get("/api/admin/wallets/{id}/transactions", owner.getId()).header("Authorization", staff),
                get("/api/admin/wallet/{username}/transactions", owner.getUsername()).header("Authorization", staff));
        mvc.perform(get("/api/admin/wallets/{id}", 999_999_999L).header("Authorization", staff))
                .andExpect(status().isNotFound());
    }

    @Test
    void petSearchIncludesOwnerId() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        mvc.perform(get("/api/admin/members/search-by-pet").param("keyword", dog.getName())
                        .header("Authorization", staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerId").value(owner.getId()));
    }

    @Test
    void lockedItemUsesPutAndDelete() throws Exception {
        PetResponse dog = testData.dog(testData.customer());
        String staff = staffToken();
        String items = body(get("/api/grooming-items").param("includeNonBookable", "true")
                .header("Authorization", staff));
        Number itemId = JsonPath.read(items, "$[0].id");

        mvc.perform(put("/api/admin/pets/{id}/locked-item", dog.getId()).header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groomingItemId\":" + itemId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedGroomingItemId").value(itemId.longValue()));
        mvc.perform(delete("/api/admin/pets/{id}/locked-item", dog.getId()).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedGroomingItemId").doesNotExist());
    }

    @Test
    void groomingItemsQueryParamMatchesOldPath() throws Exception {
        String token = tokenFor(testData.customer());
        assertSameBody(get("/api/grooming-items").param("includeNonBookable", "true").header("Authorization", token),
                get("/api/grooming-items/all").header("Authorization", token));
    }

    @Test
    void adminPathRejectsCustomers() throws Exception {
        mvc.perform(get("/api/admin/staff").header("Authorization", tokenFor(testData.customer())))
                .andExpect(status().isForbidden());
    }

    private String assertSameBody(RequestBuilder newPath, RequestBuilder oldPath) throws Exception {
        String expected = body(oldPath);
        String actual = body(newPath);
        assertThat(actual).isEqualTo(expected);
        return actual;
    }

    private String body(RequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String staffToken() {
        return "Bearer " + jwtUtils.generateToken("path-test-staff@test.local", UserRole.STAFF.name());
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getUsername(), user.getRole().name());
    }
}
