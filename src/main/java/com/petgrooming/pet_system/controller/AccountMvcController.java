package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.ChangePasswordRequest;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 員工／管理員登入後，自行修改自己的密碼（需先驗證舊密碼）
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountMvcController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /account/password ──────────────────────────────────────────────
    @GetMapping("/password")
    public String changePasswordPage(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null || user.isCustomer()) return "redirect:/dashboard";
        model.addAttribute("user", user);
        return "account/change-password";
    }

    // ── POST /account/password ─────────────────────────────────────────────
    @PostMapping("/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletRequest request,
                                 Model model) {
        User user = getLoginUser(request);
        if (user == null || user.isCustomer()) return "redirect:/dashboard";

        model.addAttribute("user", user);

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMsg", "兩次輸入的新密碼不一致");
            return "account/change-password";
        }

        try {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword(oldPassword);
            req.setNewPassword(newPassword);
            userService.changePassword(user.getUsername(), req);
            operationLogService.log(user, "AUTH", "CHANGE_PASSWORD", user.getUsername(), null);
            model.addAttribute("successMsg", "密碼修改成功，下次登入請使用新密碼");
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "account/change-password";
    }

    // ── GET /account/pin ──────────────────────────────────────────────────
    // 新需求：設定共用平板快速切換使用者用的 PIN 碼
    @GetMapping("/pin")
    public String setPinPage(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null || user.isCustomer()) return "redirect:/dashboard";
        model.addAttribute("user", user);
        model.addAttribute("hasPin", user.getSwitchPin() != null);
        return "account/set-pin";
    }

    // ── POST /account/pin ───────────────────────────────────────────────────
    @PostMapping("/pin")
    public String setPin(@RequestParam String currentPassword,
                         @RequestParam String pin,
                         @RequestParam String confirmPin,
                         HttpServletRequest request,
                         Model model) {
        User user = getLoginUser(request);
        if (user == null || user.isCustomer()) return "redirect:/dashboard";

        model.addAttribute("user", user);
        model.addAttribute("hasPin", user.getSwitchPin() != null);

        if (!pin.equals(confirmPin)) {
            model.addAttribute("errorMsg", "兩次輸入的 PIN 碼不一致");
            return "account/set-pin";
        }

        try {
            userService.setSwitchPin(user.getUsername(), currentPassword, pin);
            operationLogService.log(user, "AUTH", "SET_SWITCH_PIN", user.getUsername(), null);
            model.addAttribute("successMsg", "PIN 碼設定成功，之後可以用它快速切換身份");
            model.addAttribute("hasPin", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "account/set-pin";
    }
}
