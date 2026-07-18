package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/wallets")
@RequiredArgsConstructor
public class WalletMvcController {

    private final WalletService walletService;
    private final UserService userService;
    private final PetService petService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /admin/wallets ─────────────────────────────────────────────────
    // 顧客儲值管理首頁：列出所有 CUSTOMER，可搜尋
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String listCustomers(HttpServletRequest request, Model model,
                                @RequestParam(required = false) String keyword) {
        model.addAttribute("user", getLoginUser(request));

        var customers = userService.getAllCustomers(); // 下面 UserService 補這個方法
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            customers = customers.stream()
                    .filter(u -> u.getName().toLowerCase().contains(kw)
                            || u.getUsername().toLowerCase().contains(kw))
                    .toList();
        }
        // 帶入每位顧客的錢包資料，讓列表可直接標示低餘額（< $2,000）會員
        var walletsByUsername = new java.util.HashMap<String, com.petgrooming.pet_system.dto.WalletResponse>();
        for (var c : customers) {
            walletsByUsername.put(c.getUsername(), walletService.getWallet(c.getUsername()));
        }

        model.addAttribute("customers", customers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("walletsByUsername", walletsByUsername);
        return "admin/wallets";
    }

    // ── GET /admin/wallets/{username} ──────────────────────────────────────
    // 查看特定顧客的錢包詳細資料
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}")
    public String walletDetail(@PathVariable String username,
                               HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("customer", userService.getUserEntityByUsername(username));
        model.addAttribute("wallet", walletService.getWallet(username));
        model.addAttribute("transactions", walletService.getTransactions(username));
        model.addAttribute("depositRequest", new DepositRequest());
        // 需求 2：該會員的寵物清單，供店家設定毛長
        model.addAttribute("pets", petService.getMyPets(username));
        model.addAttribute("coatTypes", CoatType.values());
        // 需求 8：會員特殊備注（僅後台可見）
        model.addAttribute("adminNote", userService.getAdminNote(username));
        return "admin/wallet-detail";
    }

    // ── POST /admin/wallets/{username}/pets/{petId}/coat-type ──────────────
    // 需求 2：店家定義寵物毛長
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/pets/{petId}/coat-type")
    public String setCoatType(@PathVariable String username, @PathVariable Long petId,
                              @RequestParam CoatType coatType, RedirectAttributes ra) {
        try {
            petService.setCoatType(petId, coatType);
            ra.addFlashAttribute("successMsg", "毛長已更新");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets/" + username;
    }

    // ── POST /admin/wallets/{username}/note ─────────────────────────────────
    // 需求 8：店家後台備注會員特殊資訊
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/note")
    public String setAdminNote(@PathVariable String username,
                               @RequestParam(required = false) String adminNote,
                               RedirectAttributes ra) {
        try {
            userService.setAdminNote(username, adminNote);
            ra.addFlashAttribute("successMsg", "會員備注已更新");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets/" + username;
    }

    // ── POST /admin/wallets/{username}/deposit ─────────────────────────────
    // 幫顧客儲值
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/deposit")
    public String deposit(@PathVariable String username,
                          @Valid @ModelAttribute DepositRequest req,
                          RedirectAttributes ra) {
        try {
            var result = walletService.deposit(username, req);
            ra.addFlashAttribute("successMsg",
                    "儲值成功！目前餘額 $" + result.getBalance() +
                    "，會員等級：" + result.getCardTierLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "儲值失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets/" + username;
    }
}
