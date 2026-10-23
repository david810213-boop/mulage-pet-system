package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.ClaimByPhoneRequest;
import com.petgrooming.pet_system.dto.LineBindRequest;
import com.petgrooming.pet_system.dto.LineLoginRequest;
import com.petgrooming.pet_system.dto.LineLoginResponse;
import com.petgrooming.pet_system.dto.LineVerifyResponse;
import com.petgrooming.pet_system.dto.MemberLookupRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.exception.AuthException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.ClaimAttemptLimiter;
import com.petgrooming.pet_system.service.LineBindService;
import com.petgrooming.pet_system.service.MemberImportService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.CookieUtils;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.AbstractMap;
import java.util.Map;

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
    private final LineBindService lineBindService; // 店員綁定 LINE 用
    private final MemberImportService memberImportService; // 需求（追加）：老客戶認領既有匯入資料
    private final ClaimAttemptLimiter claimAttemptLimiter; // 需求（追加，2026-09-30）：查詢／認領次數限制

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
            throw new AuthException("idToken 無效或已過期");
        }

        // 2. 確認 token 是發給「我們的」LINE Login Channel，避免被其他應用程式的 token 冒用
        if (!channelId.equals(verified.getAud())) {
            log.warn("idToken aud 不符，預期: {}，實際: {}", channelId, verified.getAud());
            throw new AuthException("idToken 不屬於本系統");
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
                CookieUtils.buildJwtCookieHeader(
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
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(LineVerifyResponse.class);
    }

    // ── POST /api/line/bind ──────────────────────────────────────────────
    // 店員/店家綁定 LINE：手機上開啟綁定用的 LIFF 頁面，輸入從後台拿到的 6 位數驗證碼，
    // 驗證通過就把這支手機的 LINE userId 寫進對應店員帳號，讓低庫存等通知發得到。
    @PostMapping("/bind")
    public ResponseEntity<?> bind(@Valid @RequestBody LineBindRequest body) {
        String idToken = body.idToken();
        String code = body.code();

        LineVerifyResponse verified;
        try {
            verified = verifyIdToken(idToken);
        } catch (RestClientResponseException e) {
            log.warn("LINE idToken 驗證失敗（綁定流程）: {}", e.getMessage());
            throw new AuthException("LINE 登入驗證失敗，請重新開啟頁面再試一次");
        }
        if (!channelId.equals(verified.getAud())) {
            throw new AuthException("idToken 不屬於本系統");
        }

        User bound = lineBindService.bindByCode(code, verified.getSub());
        operationLogService.logByUsername(bound.getUsername(), "AUTH", "BIND_LINE", bound.getUsername(), null);
        return ResponseEntity.ok(Map.of("name", bound.getName()));
    }

    // ── POST /api/line/member-lookup ────────────────────────────────────
    // 需求（追加，2026-09-30）：新客報到第一步——輸入電話，判斷是既有會員（走認領）
    // 還是新客（走填資料）。用 POST 不用 GET，避免電話出現在網址／存取紀錄裡。
    // 每個 LINE 帳號 10 分鐘最多 5 次（跟認領共用），避免有人拿來一直換號碼試。
    @PostMapping("/member-lookup")
    public ResponseEntity<?> memberLookup(@Valid @RequestBody MemberLookupRequest body, @CurrentUser String username) {
        if (username == null) {
            throw new AuthException("請先登入");
        }
        String phone = body.phone();
        if (!MemberImportService.normalizePhone(phone).matches("09\\d{8}")) {
            throw new MemberException(ErrorCode.VALIDATION_FAILED, "請輸入 09 開頭的 10 碼手機號碼");
        }
        if (!claimAttemptLimiter.tryAcquire(username)) {
            throw new MemberException(ErrorCode.TOO_MANY_REQUESTS, "嘗試次數太多，請 10 分鐘後再試，或直接聯絡店家協助");
        }
        User current = userService.getUserEntityByUsername(username);
        return ResponseEntity.ok(memberImportService.lookupForClaim(current, phone));
    }

    // ── POST /api/line/claim-by-phone ───────────────────────────────────
    // 需求（追加）：老客戶用 LINE 登入後（系統會自動幫他建一筆空白新帳號，這是
    // 既有機制，這裡不動），如果他填了電話號碼，比對到店家批次匯入的舊資料，
    // 就把舊資料（姓名、寵物）整批過戶到目前這筆帳號上，並清掉那筆匯入用的
    // 暫時帳號，避免一個人對應兩筆重複的會員資料。
    // 需要先登入（LoginInterceptor 驗證 JWT），從 request 屬性拿目前登入的 username。
    // 需求（追加，2026-09-30）：多一個 verification（毛孩名字或姓名），規則見
    // MemberImportService#claimByPhone；確認失敗也算一次嘗試，跟查詢共用次數限制。
    @PostMapping("/claim-by-phone")
    public ResponseEntity<?> claimByPhone(@Valid @RequestBody ClaimByPhoneRequest body, @CurrentUser String username) {
        if (username == null) {
            throw new AuthException("請先登入");
        }
        String phone = body.phone();
        if (!claimAttemptLimiter.tryAcquire(username)) {
            throw new MemberException(ErrorCode.TOO_MANY_REQUESTS, "嘗試次數太多，請 10 分鐘後再試，或直接聯絡店家協助");
        }
        User current = userService.getUserEntityByUsername(username);
        User claimed = memberImportService.claimByPhone(current, phone, body.verification());
        claimAttemptLimiter.reset(username);
        operationLogService.logByUsername(username, "CUSTOMER", "CLAIM_MEMBER_DATA", claimed.getName(), phone);
        return ResponseEntity.ok(UserResponse.from(claimed));
    }
}
