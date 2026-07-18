package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.GroomingItemRequest;
import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMvcController {

    private final UserService userService;
    private final PaymentService paymentService;
    private final GroomingService groomingService;

    /**
     * JWT 版獲取當前登入使用者
     * 從 LoginInterceptor 存入的 request attribute 拿取 username，
     * 再用 UserService 查出完整的 User entity
     */
    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        User user = getLoginUser(request);
        return user != null && user.isAdmin();
    }

    /**
     * 1. 首頁：列出使用者、員工，【新增】同時列出所有美容項目與表單對象
     */
    @GetMapping
    public String adminHome(HttpServletRequest request, Model model) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        model.addAttribute("allUsers", userService.getAllUsers());
        model.addAttribute("allStaff", userService.getAllStaff());
        model.addAttribute("createStaffRequest", new CreateStaffRequest());
        model.addAttribute("groomingItems", groomingService.getAllItems());
        //接收服务项目的空物件
        model.addAttribute("groomingItemRequest", new GroomingItemRequest());
        
        return "admin/home";
    }

    // 處理新增員工表單提交
    @PostMapping("/staff/submit")
    public String createStaff(@Valid @ModelAttribute CreateStaffRequest req,
                              BindingResult bindingResult,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                              Model model) {

        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            return "admin/home";
        }

        try {
            userService.createStaff(req);
            redirectAttributes.addFlashAttribute("successMsg", "員工帳號新增成功");
            return "redirect:/admin";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /**
     * 重設員工／管理員密碼（ADMIN 限定，不需驗證舊密碼）
     */
    @PostMapping("/staff/{id}/reset-password")
    public String resetStaffPassword(@PathVariable Long id,
                                     @RequestParam String newPassword,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        if (!isAdmin(request)) return "redirect:/dashboard";

        try {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword(newPassword);
            userService.resetPassword(id, req);
            redirectAttributes.addFlashAttribute("successMsg", "密碼重設成功");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin";
    }

    /**
     * 2. 處理【修改】美容項目表單提交
     */
    @PostMapping("/grooming/update/{id}")
    public String updateItem(@PathVariable Long id,
                             @Valid @ModelAttribute("groomingItemRequest") GroomingItemRequest req,
                             BindingResult bindingResult,
                             HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                             Model model) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        // 如果管理員欄位填錯（例如價格填負數）
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", "服務項目修改資料格式錯誤！");
            return "admin/home"; // 留在原地
        }

        try {
            groomingService.updateItem(id, req);
            redirectAttributes.addFlashAttribute("successMsg", "服務項目修改成功！");
            return "redirect:/admin"; // PRG 模式：重導向回管理首頁
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /**
     * 3. 處理【邏輯刪除】美容項目
     * 網頁刪除按鈕會包在一個 POST 表單裡送過來
     */
    @PostMapping("/grooming/delete/{id}")
    public String deleteItem(@PathVariable Long id,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";

        try {
            groomingService.deleteItem(id);
            redirectAttributes.addFlashAttribute("successMsg", "服務項目已成功下架！");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin"; // 刪除完畢後刷新首頁
    }

    // 財務報表頁面
    @GetMapping("/report")
    public String report(HttpServletRequest request, Model model) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        model.addAttribute("report", paymentService.getFinancialReport());
        return "admin/report";
    }

        /**
     * 4. 處理【新增】美容服務項目表單提交
     */
    @PostMapping("/grooming/submit")
    public String createGroomingItem(@Valid @ModelAttribute("groomingItemRequest") GroomingItemRequest req,
                                    BindingResult bindingResult,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        // 欄位校正，萬一管理員沒填名稱或價格寫負數
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", "新增失敗：請檢查輸入欄位是否正確！");
            return "admin/home"; // 留在原地改考卷
        }

        try {
            groomingService.createItem(req);
            redirectAttributes.addFlashAttribute("successMsg", "✨ 成功建立新的美容項目！");
            return "redirect:/admin";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }
}