package com.petgrooming.pet_system.interceptor;

import com.petgrooming.pet_system.annotation.RequireCsrf;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 需求（追加，2026-09-17）：CSRF Token 防護攔截器。
 *
 * 運作方式：
 * 1. 只處理有 @RequireCsrf 的方法（見該注解說明，只標在高風險端點上）
 * 2. 從 request 屬性取出 LoginInterceptor 已經解析好、驗證過簽章的 csrf claim
 *    （這個值只有在登入當下由伺服器產生、寫進 JWT，外部無法偽造）
 * 3. 跟這次請求另外帶來的 token（header X-CSRF-Token 優先，或表單欄位 _csrf）
 *    比對，兩者一致才放行
 *
 * 為什麼這樣就有防護效果：攻擊者可以讓受害者的瀏覽器自動帶上 JWT_TOKEN
 * Cookie（如果沒有 SameSite 擋的話），但攻擊頁面讀不到 XSRF-TOKEN 這個
 * Cookie 的實際內容（跨網域，瀏覽器同源政策擋下），沒辦法在偽造的請求裡
 * 附上正確的驗證值，因此就算 SameSite 這層被繞過，這裡還有第二層防護。
 *
 * 必須排在 LoginInterceptor 之後（見 WebConfig 的 order 設定），
 * 才讀得到 tokenCsrf 這個屬性。
 */
@Component
@Slf4j
public class CsrfInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireCsrf requireCsrf = handlerMethod.getMethodAnnotation(RequireCsrf.class);
        if (requireCsrf == null) {
            return true;
        }

        String expected = (String) request.getAttribute("tokenCsrf");
        String submitted = request.getHeader("X-CSRF-Token");
        if (submitted == null || submitted.isBlank()) {
            submitted = request.getParameter("_csrf");
        }

        boolean valid = expected != null && expected.equals(submitted);

        if (!valid) {
            log.warn("CsrfInterceptor：CSRF token 驗證失敗，拒絕存取 {}", request.getRequestURI());
            boolean isApi = request.getRequestURI().startsWith("/api/");
            if (isApi) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"message\":\"驗證失效，請重新整理頁面後再試一次\"}");
            } else {
                response.sendRedirect("/dashboard?error=csrf");
            }
            return false;
        }

        return true;
    }
}
