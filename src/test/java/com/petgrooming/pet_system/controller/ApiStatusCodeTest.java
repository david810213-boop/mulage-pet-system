package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.model.User;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新增成功回 201 並帶回新資料，刪除成功回 204 不帶內容。前端一律用 res.ok 判斷成功，兩者都涵蓋在內。
 */
class ApiStatusCodeTest extends IntegrationTestBase {

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
    void addingPetIs201WithBody() throws Exception {
        User owner = testData.customer();
        mvc.perform(post("/api/pets").header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小白","petType":"DOG","breed":"柴犬","weight":8,"age":2}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("小白"));
    }

    @Test
    void deletingOwnPetIs204WithoutBody() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        mvc.perform(delete("/api/pets/{id}", dog.getId()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void bookingIs201WithBody() throws Exception {
        User owner = testData.customer();
        PetResponse dog = testData.dog(owner);
        LocalDate date = testData.openFutureDate();
        String body = """
                {"petId":%d,"date":"%s","startTime":"12:00","endTime":"14:00","contractSignatureData":"%s"}"""
                .formatted(dog.getId(), date, TestData.SIGNATURE);
        mvc.perform(post("/api/appointments").header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getUsername(), user.getRole().name());
    }
}
