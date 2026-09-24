package com.petgrooming.pet_system.interceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 員工手機版自動導向（需求，2026-09-24）。
 *
 * 規則：
 * 1. 只處理 GET、非 /api 的頁面請求，且只對 ADMIN / STAFF 生效（顧客不受影響）
 * 2. 只導向「已經有手機版」的頁面，對照表見 DESKTOP_TO_MOBILE；
 *    還沒做手機版的頁面照常顯示網頁版，不會被擋住
 * 3. Cookie VIEW_MODE=desktop：員工手動選了網頁版，一律不導向
 *    Cookie VIEW_MODE=mobile：員工手動選了手機版，不管什麼裝置都導向
 *    沒有 Cookie：依 User-Agent 判斷是不是手機
 *
 * 裝置判斷刻意只認「手機」（iPhone / Android 手機），iPad 維持網頁版。
 * 判斷完全在伺服器端用 User-Agent 做，不依賴瀏覽器的觸控偵測，
 * 避開 iPadOS 偽裝成桌面裝置那一類問題。
 */
@Component
public class MobileRedirectInterceptor implements HandlerInterceptor {

    public static final String VIEW_MODE_COOKIE = "VIEW_MODE";

    // 網頁版路徑 → 手機版路徑。之後每做完一批手機版頁面，就在這裡補上對照。
    // 第一批：Dashboard → 今日；第二批：預約列表 → 手機版預約列表
    // （網頁版核對、結帳完成後會導回 /appointments，手機上就會自動回到手機版列表）
    private static final Map<String, String> DESKTOP_TO_MOBILE = Map.of(
            "/dashboard", "/m/",
            "/appointments", "/m/appointments");

    // 第三批：帶預約編號的流程頁面，用 {1} 代入網址裡的預約 id
    private static final List<Map.Entry<Pattern, String>> PATTERN_TO_MOBILE = List.of(
            Map.entry(Pattern.compile("^/appointments/(\\d+)/checkin-order$"), "/m/appointments/{1}/checkin"),
            Map.entry(Pattern.compile("^/appointments/(\\d+)/final-check$"), "/m/appointments/{1}/final-check"),
            Map.entry(Pattern.compile("^/payments/checkout/(\\d+)$"), "/m/appointments/{1}/checkout"));

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String target = resolveTarget(request.getRequestURI());
        if (target == null) {
            return true;
        }
        String role = (String) request.getAttribute("tokenRole");
        if (!"ADMIN".equals(role) && !"STAFF".equals(role)) {
            return true;
        }

        String mode = readViewMode(request);
        boolean goMobile;
        if ("desktop".equals(mode)) {
            goMobile = false;
        } else if ("mobile".equals(mode)) {
            goMobile = true;
        } else {
            goMobile = isPhone(request);
        }

        if (goMobile) {
            // 網頁版操作完成後常會帶提示訊息（successMsg / errorMsg）導回網頁版列表，
            // 這裡要再轉手一次給手機版頁面，不然訊息會在這次導向中被吃掉
            Map<String, ?> inputFlash = RequestContextUtils.getInputFlashMap(request);
            if (inputFlash != null && !inputFlash.isEmpty()) {
                FlashMap outputFlash = RequestContextUtils.getOutputFlashMap(request);
                outputFlash.putAll(inputFlash);
                RequestContextUtils.saveOutputFlashMap(target, request, response);
            }
            response.sendRedirect(target);
            return false;
        }
        return true;
    }

    private String resolveTarget(String uri) {
        String exact = DESKTOP_TO_MOBILE.get(uri);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<Pattern, String> e : PATTERN_TO_MOBILE) {
            Matcher m = e.getKey().matcher(uri);
            if (m.matches()) {
                return e.getValue().replace("{1}", m.group(1));
            }
        }
        return null;
    }

    private String readViewMode(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (VIEW_MODE_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    static boolean isPhone(HttpServletRequest request) {
        String chMobile = request.getHeader("Sec-CH-UA-Mobile");
        if ("?1".equals(chMobile)) {
            return true;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return false;
        }
        if (ua.contains("iPhone") || ua.contains("iPod")) {
            return true;
        }
        // Android 平板的 UA 不帶 "Mobile"，只有 Android 手機才帶
        return ua.contains("Android") && ua.contains("Mobile");
    }
}
