package com.petgrooming.pet_system.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    // 密鑰改由環境變數 JWT_SECRET 提供（正式環境務必自行設定，不可沿用預設值）。
    // 保留一個明顯標記為「不安全」的預設值只是讓本機第一次啟動不會直接掛掉，
    // 正式環境如果沒設定 JWT_SECRET，啟動時會印出警告提醒。
    @Value("${JWT_SECRET:INSECURE_DEFAULT_DO_NOT_USE_IN_PRODUCTION_please_set_JWT_SECRET_env_var}")
    private String secretString;

    private Key key;

    @PostConstruct
    public void init() {
        if (secretString.startsWith("INSECURE_DEFAULT")) {
            log.warn("⚠️⚠️⚠️ 尚未設定環境變數 JWT_SECRET，目前使用不安全的預設密鑰！" +
                    "正式環境請務必設定一組隨機產生、至少 32 字元的 JWT_SECRET，否則任何人都能偽造登入憑證。");
        }
        this.key = Keys.hmacShaKeyFor(secretString.getBytes());
    }

    // Token 有效時間：設定為 24 小時（單位：毫秒）
    private static final long EXPIRATION_TIME = 86400000;

    /**
     * 生成 Token (給 AuthMvcController 登入成功時呼叫)
     * 
     * @param username 使用者帳號 (Subject)
     * @param role     使用者角色 (例如: ADMIN, USER)
     * @return 加密後的 JWT 字串
     */
    public String generateToken(String username, String role) {
        return generateToken(username, role, "WEB");
    }

    /**
     * 生成 Token，並標記來源 (給 AuthMvcController / LineAuthController 登入成功時呼叫)
     *
     * @param username 使用者帳號 (Subject)
     * @param role     使用者角色 (例如: ADMIN, STAFF, CUSTOMER)
     * @param source   登入來源："WEB"（店家後台帳密登入）或 "LINE"（顧客 LIFF 登入），
     *                 之後可用來區分請求來源，做差異化邏輯
     * @return 加密後的 JWT 字串
     */
    public String generateToken(String username, String role, String source) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role); // 把角色權限塞進 Payload
        claims.put("source", source);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析並驗證 Token (給 Interceptor 攔截器檢查通行證時呼叫)
     * 
     * @param token 前端帶過來的 JWT 字串
     * @return 裡面的 Claims 資料主體，如果驗證失敗會回傳 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            // 當 Token 過期、被篡改、格式不對，都會觸發異常
            System.out.println("JWT 驗證失敗原因: " + e.getMessage());
            return null; // 驗證失敗就回傳 null
        }
    }
}
