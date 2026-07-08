package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.LineLoginRequest;
import com.petgrooming.pet_system.dto.LineLoginResponse;
import com.petgrooming.pet_system.dto.LineVerifyResponse;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.AbstractMap;

/**
 * 顧客端 LINE 登入入口。
 * 給 LIFF 前端呼叫：帶 idToken 換取本系統自己的 JWT。
 *
 * 流程：
 * 1. LIFF 前端用 liff.getIDToken() 取得 idToken
 * 2. 呼叫本 controller 的 /api/line/login，帶上 idToken
 * 3. 後端打 LINE 官方 verify API 驗證 idToken 合法性，拿到 sub（LINE userId）
 * 4. 依 sub 查找 / 自動建立 CUSTOMER 會員
 * 5. 簽發本系統 JWT，回傳給前端（同時也寫進 Cookie，方便日後切到一般瀏覽器頁面）
 */
@Slf4j
@RestController
@RequestMapping("/api/line")
@RequiredArgsConstructor
public class LineAuthController {

    private static final String LINE_VERIFY_URL = "https://api.line.me/oauth2/v2.1/verify";

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final RestClient restClient = RestClient.create();

    @Value("${line.channel-id}")
    private String channelId;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LineLoginRequest req,
            HttpServletResponse response) {

        // 1. 向 LINE 官方驗證 idToken（不可信任前端自己宣稱的 userId）
        LineVerifyResponse verified;
        try {
            verified = verifyIdToken(req.getIdToken());
        } catch (RestClientResponseException e) {
            log.warn("LINE idToken 驗證失敗: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("idToken 無效或已過期");
        }

        // 2. 確認 token 是發給「我們的」LINE Login Channel，避免被其他應用程式的 token 冒用
        if (!channelId.equals(verified.getAud())) {
            log.warn("idToken aud 不符，預期: {}，實際: {}", channelId, verified.getAud());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("idToken 不屬於本系統");
        }

        // 3. 依 LINE userId 查找會員，找不到就自動建立
        AbstractMap.SimpleEntry<User, Boolean> result =
                userService.findOrCreateByLine(verified.getSub(), verified.getName());
        User user = result.getKey();
        boolean isNewMember = result.getValue();

        // 4. 簽發本系統的 JWT（標記 source=LINE）
        String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name(), "LINE");

        // 5. 同時寫入 Cookie，方便之後若有需要轉跳一般網頁版時沿用登入狀態
        Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(86400);
        response.addCookie(jwtCookie);

        LineLoginResponse body = new LineLoginResponse(token, UserResponse.from(user), isNewMember);
        return ResponseEntity.ok(body);
    }

    private LineVerifyResponse verifyIdToken(String idToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id_token", idToken);
        form.add("client_id", channelId);

        return restClient.post()
                .uri(LINE_VERIFY_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(LineVerifyResponse.class);
    }
}
