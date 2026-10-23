package com.petgrooming.pet_system.interceptor;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * 角色權限攔截器（JWT 版）
 * * 運作方式：
 * 1. 只處理有 @RequireRole 的方法或類別
 * 2. 從 request 屬性取出由 LoginInterceptor 解析好的角色
 * 3. 比對角色，不符合則導回 /dashboard
 */
@Component
@Slf4j
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        // 只處理 Controller 方法，靜態資源直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 1. 檢查是否有 @RequireRole 註解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        // 沒有註解，代表不需要特定角色，直接放行
        if (requireRole == null) {
            return true;
        }

        // 2. 從 request 取出剛剛 LoginInterceptor 解析出來的資訊
        String username = (String) request.getAttribute("tokenUsername");
        String roleStr = (String) request.getAttribute("tokenRole");
        boolean isApi = request.getRequestURI().startsWith("/api/");

        if (username == null || roleStr == null) {
            log.warn("RoleInterceptor：找不到 JWT 認證資訊，拒絕存取 {}", request.getRequestURI());
            if (isApi) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "請先登入");
            } else {
                response.sendRedirect("/auth/login");
            }
            return false;
        }

        // 3. 比對角色是否符合
        UserRole userRole = UserRole.valueOf(roleStr); // 將字串轉回 Enum
        UserRole[] required = requireRole.value();
        boolean hasPermission = Arrays.asList(required).contains(userRole);

        if (!hasPermission) {
            log.warn("RoleInterceptor：使用者 {} (角色: {}) 嘗試存取需要角色 {} 的路徑: {}",
                    username, userRole,
                    Arrays.toString(required), request.getRequestURI());

            if (isApi) {
                // API 路徑：回 403 JSON，不能用 redirect，前端 fetch 無法處理 HTML 重導頁面
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "權限不足，無法存取此功能");
            } else {
                // 一般網頁路徑（店家後台）：導回首頁並帶錯誤訊息
                response.sendRedirect("/dashboard?error=forbidden");
            }
            return false;
        }

        log.debug("RoleInterceptor：使用者 {} 通過角色驗證，存取 {}", username, request.getRequestURI());
        return true;
    }

    private void writeJsonError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
