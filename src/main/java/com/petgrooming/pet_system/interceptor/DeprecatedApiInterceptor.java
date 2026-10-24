package com.petgrooming.pet_system.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 舊 API 路徑仍可使用，但每次被呼叫都記一筆 warn，用來觀察是否還有舊版頁面在呼叫。
 * LIFF 頁面可能被 LINE 內建瀏覽器快取，舊路徑先保留一個版本週期，log 確認歸零後再移除舊路徑。
 * log 只記規則名稱，不記實際網址（舊路徑網址可能帶有會員帳號）。
 */
@Component
@Slf4j
public class DeprecatedApiInterceptor implements HandlerInterceptor {

    private record Rule(String method, Pattern path, String oldPath, String replacement) {
        boolean matches(HttpServletRequest request) {
            return (method == null || method.equals(request.getMethod()))
                    && path.matcher(request.getRequestURI()).matches();
        }
    }

    private static final List<Rule> RULES = List.of(
            rule(null, "/api/topup", "/api/topup", "/api/top-ups"),
            rule(null, "/api/topup/my", "/api/topup/my", "/api/me/top-ups"),
            rule(null, "/api/admin/topup/.+", "/api/admin/topup/**", "/api/admin/top-ups/**"),
            rule(null, "/api/admin/wallet/.+", "/api/admin/wallet/{username}/**", "/api/admin/wallets/{userId}/**"),
            rule(null, "/api/admin/members/[^/]*\\D[^/]*/(pets|note)",
                    "/api/admin/members/{username}/**", "/api/admin/members/{userId}/**"),
            rule(null, "/api/appointments/my", "/api/appointments/my", "/api/me/appointments"),
            rule("GET", "/api/appointments", "GET /api/appointments", "/api/admin/appointments"),
            rule(null, "/api/appointments/\\d+/detail", "/api/appointments/{id}/detail", "/api/appointments/{id}"),
            rule(null, "/api/pets/my", "/api/pets/my", "/api/me/pets"),
            rule(null, "/api/payments/my", "/api/payments/my", "/api/me/transactions"),
            rule(null, "/api/payments/(transactions|report)", "/api/payments/{transactions|report}",
                    "/api/admin/payments/{transactions|report}"),
            rule(null, "/api/users", "/api/users", "/api/admin/users"),
            rule(null, "/api/users/staff", "/api/users/staff", "/api/admin/staff"),
            rule(null, "/api/admin/pets/\\d+/lock-grooming-item", "PUT .../lock-grooming-item",
                    "PUT /api/admin/pets/{id}/locked-item"),
            rule(null, "/api/admin/pets/\\d+/unlock-grooming-item", "PUT .../unlock-grooming-item",
                    "DELETE /api/admin/pets/{id}/locked-item"),
            rule(null, "/api/grooming-items/all", "/api/grooming-items/all",
                    "/api/grooming-items?includeNonBookable=true"));

    private static Rule rule(String method, String regex, String oldPath, String replacement) {
        return new Rule(method, Pattern.compile(regex), oldPath, replacement);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        for (Rule r : RULES) {
            if (r.matches(request)) {
                log.warn("已淘汰的 API 仍被呼叫：{} {}，請改用 {}", request.getMethod(), r.oldPath(), r.replacement());
                break;
            }
        }
        return true;
    }
}
