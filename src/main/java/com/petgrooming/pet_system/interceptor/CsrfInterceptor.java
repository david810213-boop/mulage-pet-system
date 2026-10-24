package com.petgrooming.pet_system.interceptor;

import com.petgrooming.pet_system.annotation.SkipCsrf;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * CSRF Token 驗證。
 *
 * 瀏覽器會自動帶上 Cookie，所以「以 Cookie 登入」的寫入請求（POST／PUT／PATCH／DELETE）
 * 一律要另外帶 token：header X-CSRF-Token，或表單欄位 _csrf，值必須等於登入時簽進 JWT 的 csrf，
 * 也就是前端可讀的 XSRF-TOKEN Cookie（攻擊頁面讀不到別的網域的 Cookie，所以帶不出正確的值）。
 * 員工頁面共用的 /js/csrf.js 會自動幫所有表單與 fetch 補上，個別頁面不用自己處理。
 *
 * 以 Authorization: Bearer 登入的請求（LIFF）略過：瀏覽器不會自動帶這個標頭，跨站請求帶不出來。
 * 未登入的請求也略過，沒有登入狀態可以被冒用。確定不能帶 token 的端點標 @SkipCsrf。
 */
@Component
@Slf4j
public class CsrfInterceptor implements HandlerInterceptor {

    /** LoginInterceptor 在 token 來自 JWT_TOKEN Cookie 時設定的 request 屬性。 */
    public static final String TOKEN_FROM_COOKIE = "tokenFromCookie";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String MESSAGE = "驗證失效，請重新整理頁面後再試一次";

    private static final String ERROR_PAGE = """
            <!doctype html>
            <html lang="zh-TW"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>操作沒有送出 | 慕沐村 Mulage pet</title></head>
            <body style="font-family:'Noto Sans TC','PingFang TC','Microsoft JhengHei',sans-serif;padding:32px 16px;color:#2b2b2b;">
            <h2 style="margin:0 0 12px;">操作沒有送出</h2>
            <p>頁面的驗證資訊已失效（可能是登入過期或頁面開太久），剛才的操作沒有執行。</p>
            <p>請回上一頁重新整理後再操作一次；若仍出現此畫面，請重新登入。</p>
            <p><a href="javascript:history.back()">回上一頁</a>　<a href="/dashboard">回首頁</a>　<a href="/auth/login">重新登入</a></p>
            </body></html>
            """;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (!Boolean.TRUE.equals(request.getAttribute(TOKEN_FROM_COOKIE))) {
            return true;
        }
        if (handlerMethod.hasMethodAnnotation(SkipCsrf.class)
                || handlerMethod.getBeanType().isAnnotationPresent(SkipCsrf.class)) {
            return true;
        }

        String expected = (String) request.getAttribute("tokenCsrf");
        String submitted = request.getHeader("X-CSRF-Token");
        if (submitted == null || submitted.isBlank()) {
            submitted = request.getParameter("_csrf");
        }

        if (matches(expected, submitted)) {
            return true;
        }

        log.warn("CsrfInterceptor：CSRF token 驗證失敗，拒絕存取 {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        if (request.getRequestURI().startsWith("/api/")) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"detail\":\"" + MESSAGE
                    + "\",\"message\":\"" + MESSAGE + "\"}");
        } else {
            // 直接顯示原因，不轉址到首頁：轉址後店員看不出剛才的操作沒有送出
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(ERROR_PAGE);
        }
        return false;
    }

    private static boolean matches(String expected, String submitted) {
        if (expected == null || expected.isEmpty() || submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
