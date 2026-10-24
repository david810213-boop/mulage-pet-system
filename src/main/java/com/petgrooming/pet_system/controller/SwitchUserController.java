package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.CookieUtils;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * 共用平板（例如店內 iPad）快速切換使用者。
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

    // ── GET /auth/switch-user ─────────────────────────────────────────────
    // 列出所有「已設定 PIN 碼」的員工/管理員，供點選切換
    @GetMapping
    public String switchUserPage(Model model, @CurrentUser User currentUser) {
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
                           HttpServletResponse response,
                           RedirectAttributes ra,
                           @CurrentUser User currentUser) {
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

        // 驗證通過：直接簽發目標帳號的 JWT，換掉目前的登入 Cookie。
        // 同時重新產生 csrfToken 並更新 XSRF-TOKEN Cookie，否則切換後的帳號在高風險操作
        // （密碼變更、帳號合併、手動儲值、刪除）上會因為 token 對不上被擋下；Cookie 一律用
        // CookieUtils 寫入（帶 SameSite=Lax），跟登入流程一致。
        String csrfToken = UUID.randomUUID().toString();
        String token = jwtUtils.generateToken(target.getUsername(), target.getRole().name(), "WEB", csrfToken);

        response.addHeader("Set-Cookie",
                CookieUtils.buildJwtCookieHeader(
                        "JWT_TOKEN", token, 86400, cookieSecure));
        response.addHeader("Set-Cookie",
                CookieUtils.buildReadableCookieHeader(
                        "XSRF-TOKEN", csrfToken, 86400, cookieSecure));

        operationLogService.log(currentUser, "AUTH", "SWITCH_USER",
                target.getUsername(), "由 " + currentUser.getUsername() + " 切換身份");

        ra.addFlashAttribute("successMsg", "已切換為 " + target.getName());
        return "redirect:/dashboard";
    }
}
