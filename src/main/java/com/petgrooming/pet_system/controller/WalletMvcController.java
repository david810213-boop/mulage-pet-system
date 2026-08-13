package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
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
    private final TopUpService topUpService;
    private final PetGroomingNoteRepository petGroomingNoteRepository;
    private final OperationLogService operationLogService;

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
        // 統整需求：儲值管理頁一併帶出待審核的線上轉帳儲值申請
        model.addAttribute("pendingTopups", topUpService.pending());
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
        var pets = petService.getMyPets(username);
        model.addAttribute("pets", pets);
        model.addAttribute("coatTypes", CoatType.values());
        // 進行中核對功能：帶出每隻寵物的美容狀況歷史，供店家查詢客製化美容參考
        var groomingNotesByPetId = new java.util.HashMap<Long, java.util.List<com.petgrooming.pet_system.model.PetGroomingNote>>();
        for (var p : pets) {
            groomingNotesByPetId.put(p.getId(),
                    petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(p.getId()));
        }
        model.addAttribute("groomingNotesByPetId", groomingNotesByPetId);
        // 需求 8：會員特殊備注（僅後台可見）
        model.addAttribute("adminNote", userService.getAdminNote(username));
        return "admin/wallet-detail";
    }

    // ── POST /admin/wallets/{username}/pets/{petId}/coat-type ──────────────
    // 需求 2：店家定義寵物毛長
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/pets/{petId}/coat-type")
    public String setCoatType(@PathVariable String username, @PathVariable Long petId,
                              @RequestParam CoatType coatType,
                              @RequestParam(required = false) String returnTo,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            petService.setCoatType(petId, coatType);
            operationLogService.log(user, "CUSTOMER", "SET_COAT_TYPE",
                    "會員 " + username + " 的寵物 #" + petId, coatType.name());
            ra.addFlashAttribute("successMsg", "毛長已更新");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:" + (returnTo != null && !returnTo.isBlank()
                ? returnTo : "/admin/wallets/" + username);
    }

    // ── POST /admin/wallets/{username}/pets/{petId}/photo ──────────────────
    // 需求 17：店家後台可更新照片（不限本人上傳，方便店家幫忙補拍/更新）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/pets/{petId}/photo")
    public String uploadPetPhoto(@PathVariable String username, @PathVariable Long petId,
                                 @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                 @RequestParam(required = false) String returnTo,
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            petService.updatePhoto(petId, file);
            operationLogService.log(user, "CUSTOMER", "UPLOAD_PET_PHOTO",
                    "會員 " + username + " 的寵物 #" + petId, null);
            ra.addFlashAttribute("successMsg", "照片已更新");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", "上傳失敗：" + e.getMessage());
        }
        return "redirect:" + (returnTo != null && !returnTo.isBlank()
                ? returnTo : "/admin/wallets/" + username);
    }

    // ── POST /admin/wallets/{username}/grooming-notes/{noteId}/photo ───────
    // 需求 18：美容狀況歷史相簿——幫某一筆美容備註補上照片
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/grooming-notes/{noteId}/photo")
    public String uploadGroomingNotePhoto(@PathVariable String username, @PathVariable Long noteId,
                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                          @RequestParam(required = false) String returnTo,
                                          HttpServletRequest request,
                                          RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            petService.uploadGroomingNotePhoto(noteId, file);
            operationLogService.log(user, "CUSTOMER", "UPLOAD_GROOMING_NOTE_PHOTO",
                    "會員 " + username + " 的美容紀錄 #" + noteId, null);
            ra.addFlashAttribute("successMsg", "照片已更新");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", "上傳失敗：" + e.getMessage());
        }
        return "redirect:" + (returnTo != null && !returnTo.isBlank()
                ? returnTo : "/admin/wallets/" + username);
    }

    // ── POST /admin/wallets/{username}/note ─────────────────────────────────
    // 需求 8：店家後台備注會員特殊資訊
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/note")
    public String setAdminNote(@PathVariable String username,
                               @RequestParam(required = false) String adminNote,
                               @RequestParam(required = false) String returnTo,
                               HttpServletRequest request,
                               RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            userService.setAdminNote(username, adminNote);
            operationLogService.log(user, "CUSTOMER", "SET_NOTE", "會員 " + username,
                    adminNote != null && adminNote.length() > 100 ? adminNote.substring(0, 100) + "…" : adminNote);
            ra.addFlashAttribute("successMsg", "會員備注已更新");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:" + (returnTo != null && !returnTo.isBlank()
                ? returnTo : "/admin/wallets/" + username);
    }

    // ── POST /admin/wallets/{username}/deposit ─────────────────────────────
    // 幫顧客儲值
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/deposit")
    public String deposit(@PathVariable String username,
                          @Valid @ModelAttribute DepositRequest req,
                          HttpServletRequest request,
                          RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var result = walletService.deposit(username, req);
            operationLogService.log(user, "WALLET", "DEPOSIT", "會員 " + username,
                    "+$" + req.getAmount() + "（目前餘額 $" + result.getBalance() + "）");
            ra.addFlashAttribute("successMsg",
                    "儲值成功！目前餘額 $" + result.getBalance() +
                    "，會員等級：" + result.getCardTierLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "儲值失敗：" + e.getMessage());
        }
        return "redirect:/admin/wallets/" + username;
    }
}
