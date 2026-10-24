package com.petgrooming.pet_system.security;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 權限：未登入 401、角色不符 403，並以結構檢查確保所有 /api/admin 端點都有標註角色。
 */
class ApiAuthorizationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JwtUtils jwtUtils;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** 新增的管理端點若忘了加 @RequireRole，這個測試會失敗。 */
    @Test
    void everyAdminEndpointDeclaresRequiredRoles() {
        var adminEndpoints = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> paths(entry.getKey()).stream().anyMatch(p -> p.startsWith("/api/admin")))
                .toList();
        assertThat(adminEndpoints).as("應能找到管理端點，否則本檢查形同虛設").hasSizeGreaterThan(20);

        List<String> unprotected = adminEndpoints.stream()
                .filter(entry -> !declaresRole(entry.getValue()))
                .map(entry -> entry.getKey() + " -> " + entry.getValue().getShortLogMessage())
                .toList();

        assertThat(unprotected).as("缺少 @RequireRole 的管理端點").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/appointments",
            "/api/admin/walk-in-orders",
            "/api/admin/topup/pending",
            "/api/admin/top-ups/pending",
            "/api/appointments",
            "/api/payments/transactions",
            "/api/admin/payments/transactions"
    })
    void staffEndpointsRejectAnonymousAndCustomers(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer(UserRole.CUSTOMER))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(UserRole.STAFF))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/report", "/api/users", "/api/users/staff",
            "/api/admin/payments/report", "/api/admin/users", "/api/admin/staff"})
    void adminOnlyEndpointsRejectStaff(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", bearer(UserRole.STAFF))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(UserRole.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtils.generateToken("authz-test-" + role.name().toLowerCase() + "@test.local", role.name());
    }

    private static boolean declaresRole(HandlerMethod method) {
        return method.hasMethodAnnotation(RequireRole.class)
                || method.getBeanType().isAnnotationPresent(RequireRole.class);
    }

    private static List<String> paths(RequestMappingInfo info) {
        return info.getPatternValues().stream().toList();
    }
}
