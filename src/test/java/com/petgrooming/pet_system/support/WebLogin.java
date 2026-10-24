package com.petgrooming.pet_system.support;

import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 模擬員工網頁版登入：JWT_TOKEN Cookie（內含 csrf）＋頁面上 csrf.js 會自動補的 X-CSRF-Token 標頭。
 */
public final class WebLogin {

    public static final String CSRF = "test-csrf-token";

    private WebLogin() {
    }

    public static Cookie cookie(JwtUtils jwtUtils, User user) {
        return new Cookie("JWT_TOKEN", jwtUtils.generateToken(user.getUsername(), user.getRole().name(), "WEB", CSRF));
    }

    public static MockHttpServletRequestBuilder as(JwtUtils jwtUtils, User user, MockHttpServletRequestBuilder request) {
        return request.cookie(cookie(jwtUtils, user)).header("X-CSRF-Token", CSRF);
    }
}
