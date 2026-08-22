package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.LoginRequest;
import com.petgrooming.pet_system.dto.RegisterRequest;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.JwtUtils; // 1. 引入剛剛修好的 JwtUtils
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthMvcController {

    private final UserService userService;
    private final JwtUtils jwtUtils; // 2. 注入 JwtUtils
    private final OperationLogService operationLogService;

    // 正式環境（HTTPS）務必在 Railway 環境變數設 COOKIE_SECURE=true；
    // 本機開發用 http://localhost 測試時保持預設 false，否則瀏覽器不會送出這個 Cookie，登入會直接失效。
    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String redirect,
            @RequestParam(required = false) String error,
            Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        model.addAttribute("redirect", redirect);
        if (error != null) {
            model.addAttribute("errorMsg", "帳號或密碼錯誤，請重新輸入");
        }
        return "auth/login";
    }

    @PostMapping("/login/submit")
    public String loginSubmit(@Valid @ModelAttribute LoginRequest req,
            BindingResult bindingResult,
            @RequestParam(required = false) String redirect,
            HttpServletResponse response, // 3. 換成 HttpServletResponse 來塞 Cookie
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("redirect", redirect);
            return "auth/login";
        }

        Optional<User> userOpt = userService.authenticate(req.getUsername(), req.getPassword());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // 4. 關鍵核心：生成 JWT Token
            String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name());

            operationLogService.log(user, "AUTH", "LOGIN", user.getUsername(), null);

            // 5. 將 Token 包進 Cookie 中送給瀏覽器
            // 資安修正：改用 CookieUtils 手動組 Set-Cookie 標頭，帶上 SameSite=Lax
            // （jakarta.servlet.http.Cookie 這個舊版 API 不支援設定 SameSite）。
            response.addHeader("Set-Cookie",
                    com.petgrooming.pet_system.utils.CookieUtils.buildJwtCookieHeader(
                            "JWT_TOKEN", token, 86400, cookieSecure));

            if (redirect != null && !redirect.isBlank() && !redirect.startsWith("/auth")) {
                return "redirect:" + redirect;
            }
            return "redirect:/dashboard";

        } else {
            return "redirect:/auth/login?error=true" +
                    (redirect != null ? "&redirect=" + redirect : "");
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register/submit")
    public String registerSubmit(@Valid @ModelAttribute RegisterRequest req,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("registerRequest", req);
            return "auth/register";
        }

        try {
            userService.register(req);
            redirectAttributes.addFlashAttribute("successMsg", "註冊成功！請登入");
            return "redirect:/auth/login";

        } catch (IllegalArgumentException e) {
            model.addAttribute("registerRequest", req);
            model.addAttribute("errorMsg", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        // /auth/logout 不經過 LoginInterceptor（見 WebConfig 排除清單），
        // 拿不到 request.getAttribute("tokenUsername")，改直接從 Cookie 解析 JWT
        String username = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("JWT_TOKEN".equals(c.getName())) {
                    var claims = jwtUtils.parseToken(c.getValue());
                    if (claims != null) username = claims.getSubject();
                    break;
                }
            }
        }
        operationLogService.logByUsername(username, "AUTH", "LOGOUT", username, null);

        // 6. 登出的做法：弄一個同名、時效為 0 的 Cookie 覆蓋過去，瀏覽器就會自動刪除它
        // 資安修正：比照登入時的做法，改用 CookieUtils 帶上 SameSite=Lax
        // （屬性要跟登入時設定的一致，瀏覽器才能正確比對並清除）。
        response.addHeader("Set-Cookie",
                com.petgrooming.pet_system.utils.CookieUtils.buildJwtCookieHeader(
                        "JWT_TOKEN", "", 0, cookieSecure));

        return "redirect:/auth/login";
    }
}
