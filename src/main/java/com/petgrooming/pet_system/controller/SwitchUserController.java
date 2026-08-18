package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 新需求：共用平板（例如店內 iPad）快速切換使用者。
 *
 * 安全設計重點：
 * - 這條路徑本身要求「目前裝置已經是某個店家/員工帳號登入狀態」才能使用
 *   （不是給完全未登入的人快速登入用的後門，只是「已登入的裝置」換人操作時的捷徑）。
 * - 每個員工要自己先在「帳號設定 → 設定 PIN 碼」設定過 4 位數 PIN，才會出現在切換清單裡，
 *   沒設定過 PIN 的人沒辦法被切換過去。
 * - 切換當下一樣要輸入該目標帳號自己的 PIN 碼，不是輸入目前登入者的 PIN，
 *   避免「隨便點一個人的名字就直接變成他」。
 */
@Controller
@RequestMapping("/auth/switch-user")
@RequiredArgsConstructor
public class SwitchUserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final OperationLogService operationLogService;

    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /auth/switch-user ─────────────────────────────────────────────
    // 列出所有「已設定 PIN 碼」的員工/管理員，供點選切換
    @GetMapping
    public String switchUserPage(HttpServletRequest request, Model model) {
        User currentUser = getLoginUser(request);
        if (currentUser == null || currentUser.isCustomer()) {
            return "redirect:/auth/login";
        }

        List<User> switchable = userService.getAllStaffEntities().stream()
                .filter(u -> u.getSwitchPin() != null)
                .toList();

        model.addAttribute("user", currentUser);
        model.addAttribute("switchable", switchable);
        return "auth/switch-user";
    }

    // ── POST /auth/switch-user ────────────────────────────────────────────
    @PostMapping
    public String doSwitch(@RequestParam Long targetUserId,
                           @RequestParam String pin,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes ra) {
        User currentUser = getLoginUser(request);
        if (currentUser == null || currentUser.isCustomer()) {
            return "redirect:/auth/login";
        }

        User target = userService.getUserEntityById(targetUserId);
        if (target == null || target.getSwitchPin() == null) {
            ra.addFlashAttribute("errorMsg", "找不到這個帳號，或對方尚未設定 PIN 碼");
            return "redirect:/auth/switch-user";
        }

        if (!userService.verifySwitchPin(target, pin)) {
            ra.addFlashAttribute("errorMsg", "PIN 碼錯誤，請重新輸入");
            return "redirect:/auth/switch-user";
        }

        // 驗證通過：直接簽發目標帳號的 JWT，換掉目前的登入 Cookie
        String token = jwtUtils.generateToken(target.getUsername(), target.getRole().name());

        Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(cookieSecure);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(86400);
        response.addCookie(jwtCookie);

        operationLogService.log(currentUser, "AUTH", "SWITCH_USER",
                target.getUsername(), "由 " + currentUser.getUsername() + " 切換身份");

        ra.addFlashAttribute("successMsg", "已切換為 " + target.getName());
        return "redirect:/dashboard";
    }
}
