package com.petgrooming.pet_system.utils;

/**
 * 資安修正：jakarta.servlet.http.Cookie 這個舊版 Servlet API 不支援設定
 * SameSite 屬性，本專案登入用的 JWT_TOKEN Cookie 原本因此完全沒有 SameSite，
 * 等於只靠瀏覽器自己的預設值（現代瀏覽器多半預設 Lax）擋 CSRF，不是刻意做的防護。
 *
 * 這裡直接組出完整的 Set-Cookie 標頭字串，透過 HttpServletResponse.addHeader(
 * "Set-Cookie", ...) 寫入，取代 response.addCookie(Cookie)，明確帶上
 * SameSite=Lax——店家後台是一般表單導覽（非跨站 AJAX），Lax 已經足夠擋掉
 * 常見的跨站 POST 偽造請求，同時不影響 LINE LIFF 內部瀏覽器的正常導覽情境。
 */
public final class CookieUtils {

    private CookieUtils() {
    }

    /**
     * 組出登入用的 Set-Cookie 標頭字串。
     *
     * @param name      cookie 名稱
     * @param value     cookie 值（登出時可傳空字串，搭配 maxAgeSeconds=0 立即失效）
     * @param maxAgeSeconds 有效秒數，0 代表立即失效（登出用）
     * @param secure    是否只在 HTTPS 下傳送（由呼叫端的 COOKIE_SECURE 環境變數決定）
     */
    public static String buildJwtCookieHeader(String name, String value, long maxAgeSeconds, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value == null ? "" : value);
        sb.append("; Path=/");
        sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; HttpOnly");
        sb.append("; SameSite=Lax");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
