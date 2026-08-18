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
            Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
            jwtCookie.setHttpOnly(true); // 防止前端 JavaScript 竊取，防範 XSS 攻擊
            jwtCookie.setSecure(cookieSecure); // 只在 HTTPS 連線下傳送（由 COOKIE_SECURE 環境變數控制）
            jwtCookie.setPath("/"); // 整個專案路徑都有效
            jwtCookie.setMaxAge(86400); // 有效期設為 1 天（單位：秒，與 Token 的 24 小時同步）

            response.addCookie(jwtCookie); // 真正寫入瀏覽器

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
        Cookie jwtCookie = new Cookie("JWT_TOKEN", null);
        jwtCookie.setPath("/");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(cookieSecure); // 屬性需跟登入時設定的一致，瀏覽器才能正確比對並清除
        jwtCookie.setMaxAge(0); // 設為 0 代表立即失效
        response.addCookie(jwtCookie);

        return "redirect:/auth/login";
    }
}
