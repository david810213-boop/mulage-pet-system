package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireCsrf;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.ChangePasswordRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.LineBindService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 員工手機版：帳號設定（需求，2026-09-24 第九批）。
 *
 * 修改密碼、設定 PIN 碼、綁定 LINE（收新預約／低庫存通知用），跟網頁版
 * AccountMvcController 呼叫同樣的 service。修改密碼跟網頁版一樣要求 CSRF token
 * （@RequireCsrf），token 直接從這次請求的 JWT 取出放進表單隱藏欄位。
 */
@Controller
@RequestMapping("/m/account")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileAccountController {

    /** 綁定專用的 LIFF App 網址，跟網頁版綁定頁同一個 */
    private static final String BIND_LIFF_URL = "https://liff.line.me/2010567973-bNNfTiV9";

    private final UserService userService;
    private final LineBindService lineBindService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) {
            return null;
        }
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping({ "", "/" })
    public String account(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("csrf", request.getAttribute("tokenCsrf"));
        model.addAttribute("hasPin", user.getSwitchPin() != null);
        model.addAttribute("lineBound", user.getLineUserId() != null && !user.getLineUserId().isBlank());
        model.addAttribute("bindUrl", BIND_LIFF_URL);
        model.addAttribute("activeTab", "more");
        return "m/account";
    }

    @RequireCsrf
    @PostMapping("/password")
    public String changePassword(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("toastError", "兩次輸入的新密碼不一致");
            ra.addFlashAttribute("openSheet", "sheetPwd");
            return "redirect:/m/account";
        }
        try {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword(oldPassword);
            req.setNewPassword(newPassword);
            userService.changePassword(user.getUsername(), req);
            operationLogService.log(user, "AUTH", "CHANGE_PASSWORD", user.getUsername(), "手機版");
            ra.addFlashAttribute("toast", "密碼已修改，下次登入請用新密碼");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
            ra.addFlashAttribute("openSheet", "sheetPwd");
        }
        return "redirect:/m/account";
    }

    @PostMapping("/pin")
    public String setPin(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam String currentPassword,
            @RequestParam String pin,
            @RequestParam String confirmPin) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (!pin.equals(confirmPin)) {
            ra.addFlashAttribute("toastError", "兩次輸入的 PIN 碼不一致");
            ra.addFlashAttribute("openSheet", "sheetPin");
            return "redirect:/m/account";
        }
        try {
            userService.setSwitchPin(user.getUsername(), currentPassword, pin);
            operationLogService.log(user, "AUTH", "SET_SWITCH_PIN", user.getUsername(), "手機版");
            ra.addFlashAttribute("toast", "PIN 碼已設定，之後可以用它快速切換身份");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
            ra.addFlashAttribute("openSheet", "sheetPin");
        }
        return "redirect:/m/account";
    }

    @PostMapping("/bind-line")
    public String bindLine(HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        ra.addFlashAttribute("bindCode", lineBindService.generateCode(user.getUsername()));
        return "redirect:/m/account";
    }
}
