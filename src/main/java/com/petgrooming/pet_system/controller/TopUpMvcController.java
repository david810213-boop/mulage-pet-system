package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 需求 1：店家後台審核線上轉帳儲值申請的網頁介面。
 * （對應的 REST API 在 TopUpController，這裡是給店家在瀏覽器上直接操作用的表單頁面）
 */
@Controller
@RequestMapping("/admin/topup")
@RequiredArgsConstructor
public class TopUpMvcController {

    private final TopUpService topUpService;
    private final UserService userService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /admin/topup ────────────────────────────────────────────────────
    // 待核帳清單（PENDING）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String pending(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("requests", topUpService.pending());
        return "redirect:/admin/wallets";
    }

    // ── POST /admin/topup/{id}/confirm ─────────────────────────────────────
    // 確認入帳：真正呼叫 WalletService.deposit() 加值
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, HttpServletRequest request,
                          RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var result = topUpService.confirm(id, user.getName());
            ra.addFlashAttribute("successMsg",
                    "已確認入帳：" + result.getName() + " +$" + result.getAmount());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "確認失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets";
    }

    // ── POST /admin/topup/{id}/reject ──────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            topUpService.reject(id, user.getName(), reason);
            ra.addFlashAttribute("successMsg", "已駁回此申請");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "駁回失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets";
    }
}
