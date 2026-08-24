package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.LineLoginRequest;
import com.petgrooming.pet_system.dto.LineLoginResponse;
import com.petgrooming.pet_system.dto.LineVerifyResponse;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final OperationLogService operationLogService;
    private final com.petgrooming.pet_system.service.LineBindService lineBindService; // 店員綁定 LINE 用
    private final com.petgrooming.pet_system.service.MemberImportService memberImportService; // 需求（追加）：老客戶認領既有匯入資料

    // 正式環境（HTTPS）務必在 Railway 環境變數設 COOKIE_SECURE=true；本機開發保持預設 false。
    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

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

        operationLogService.log(user, "AUTH", isNewMember ? "LINE_REGISTER" : "LINE_LOGIN",
                user.getUsername(), null);

        // 5. 同時寫入 Cookie，方便之後若有需要轉跳一般網頁版時沿用登入狀態
        // 資安修正：改用 CookieUtils 帶上 SameSite=Lax，跟 AuthMvcController 的
        // 網頁版登入/登出保持一致做法。
        response.addHeader("Set-Cookie",
                com.petgrooming.pet_system.utils.CookieUtils.buildJwtCookieHeader(
                        "JWT_TOKEN", token, 86400, cookieSecure));

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

    // ── POST /api/line/bind ──────────────────────────────────────────────
    // 店員/店家綁定 LINE：手機上開啟綁定用的 LIFF 頁面，輸入從後台拿到的 6 位數驗證碼，
    // 驗證通過就把這支手機的 LINE userId 寫進對應店員帳號，讓低庫存等通知發得到。
    @PostMapping("/bind")
    public ResponseEntity<?> bind(@RequestBody java.util.Map<String, String> body) {
        String idToken = body.get("idToken");
        String code = body.get("code");
        if (idToken == null || idToken.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "請提供驗證碼"));
        }

        LineVerifyResponse verified;
        try {
            verified = verifyIdToken(idToken);
        } catch (RestClientResponseException e) {
            log.warn("LINE idToken 驗證失敗（綁定流程）: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "LINE 登入驗證失敗，請重新開啟頁面再試一次"));
        }
        if (!channelId.equals(verified.getAud())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "idToken 不屬於本系統"));
        }

        try {
            User bound = lineBindService.bindByCode(code, verified.getSub());
            operationLogService.logByUsername(bound.getUsername(), "AUTH", "BIND_LINE", bound.getUsername(), null);
            return ResponseEntity.ok(java.util.Map.of("name", bound.getName()));
        } catch (IllegalArgumentException e) {
            // 需求（追加）：跟成功回應一樣統一回 JSON（不要跟別的端點一個回純文字一個回 JSON，
            // 這種不一致是這次「錯誤訊息被吞掉」問題的根源）
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    // ── POST /api/line/claim-by-phone ───────────────────────────────────
    // 需求（追加）：老客戶用 LINE 登入後（系統會自動幫他建一筆空白新帳號，這是
    // 既有機制，這裡不動），如果他填了電話號碼，比對到店家批次匯入的舊資料，
    // 就把舊資料（姓名、寵物）整批過戶到目前這筆帳號上，並清掉那筆匯入用的
    // 暫時帳號，避免一個人對應兩筆重複的會員資料。
    // 需要先登入（LoginInterceptor 驗證 JWT），從 request 屬性拿目前登入的 username。
    @PostMapping("/claim-by-phone")
    public ResponseEntity<?> claimByPhone(HttpServletRequest request, @RequestBody java.util.Map<String, String> body) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message", "請先登入"));
        }
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "請輸入電話號碼"));
        }
        try {
            User current = userService.getUserEntityByUsername(username);
            User claimed = memberImportService.claimByPhone(current, phone);
            operationLogService.logByUsername(username, "CUSTOMER", "CLAIM_MEMBER_DATA", claimed.getName(), phone);
            return ResponseEntity.ok(UserResponse.from(claimed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
