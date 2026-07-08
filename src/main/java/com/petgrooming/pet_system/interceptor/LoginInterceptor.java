package com.petgrooming.pet_system.interceptor;

import com.petgrooming.pet_system.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登入驗證攔截器（JWT 版）
 * 從 Cookie 獲取 JWT_TOKEN 並驗證
 */
@Component
@Slf4j
@RequiredArgsConstructor // 注入工具類別
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        log.debug("LoginInterceptor 處理請求: {}", request.getRequestURI());

        String token = null;

        // 1. 優先從 Authorization: Bearer 取 token（LIFF / 行動端常用，不依賴 Cookie）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. 找不到再從 Cookie 中尋找名為 JWT_TOKEN 的 Cookie（店家網頁版用）
        if (token == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("JWT_TOKEN".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        // 3. 如果有拿到 Token，嘗試解析它
        if (token != null) {
            Claims claims = jwtUtils.parseToken(token);

            if (claims != null) {
                // 驗證成功！將用戶資訊暫存到 request 中，傳給後續的 RoleInterceptor 或 Controller 使用
                request.setAttribute("tokenUsername", claims.getSubject());
                request.setAttribute("tokenRole", claims.get("role", String.class));
                request.setAttribute("tokenSource", claims.get("source", String.class));
                return true; // 放行
            }
        }

        // 4. 未登入或 Token 驗證失敗
        String uri = request.getRequestURI();
        log.debug("JWT 驗證失敗或未登入，來源路徑: {}", uri);

        // API 路徑（給 LIFF / 前端 fetch 呼叫）：回 401 JSON，不能用 redirect，
        // 因為 fetch 收到 30x 不會自動幫你跳轉登入頁，前端也無法正確處理 HTML 重導頁面
        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"請先登入\"}");
            return false;
        }

        // 一般網頁路徑（店家後台）：維持原本導回登入頁的行為
        response.sendRedirect("/auth/login?redirect=" + uri);
        return false;
    }
}
