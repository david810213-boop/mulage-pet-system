package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.UpdateProfileRequest;
import com.petgrooming.pet_system.enums.CustomerSource;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 網頁版「編輯個人信息」，兩種使用情境：
 *
 *   1. 會員自己用（登入自己的帳號）：直接編輯自己的資料，跟 LIFF 版邏輯一樣，
 *      不能指定別人的帳號——即使有人竄改表單裡的隱藏欄位，後端一律強制鎖定成本人帳號。
 *
 *   2. 店家/員工用（現場客人用店內 iPad，由店員操作）：情境是現場客人到店，
 *      店員先搜尋/確認這位客人的會員帳號，再「幫這個客人」填寫資料——填的是
 *      客人的資料，不是店員自己的。所以店家登入時看到的是「先搜尋會員」畫面，
 *      選定會員後才會出現可以填寫的表單，表單提交時把目標會員的 username
 *      放在隱藏欄位一併送出。
 */
@Controller
@RequestMapping("/my-profile")
@RequiredArgsConstructor
public class CustomerProfileMvcController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping
    public String editForm(@RequestParam(required = false) String username,
                           HttpServletRequest request, Model model) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) return "redirect:/auth/login?redirect=/my-profile";

        model.addAttribute("user", loginUser);
        model.addAttribute("sources", CustomerSource.values());

        if (loginUser.isCustomer()) {
            // 情境 1：會員編輯自己的資料，不用搜尋，直接顯示表單
            model.addAttribute("isStaffMode", false);
            model.addAttribute("targetUsername", loginUser.getUsername());
            model.addAttribute("profile", userService.getMe(loginUser.getUsername()));
            return "profile/edit";
        }

        // 情境 2：店家/員工——還沒選定會員之前只顯示搜尋畫面
        model.addAttribute("isStaffMode", true);
        if (username == null || username.isBlank()) {
            return "profile/edit";
        }

        try {
            var target = userService.getUserEntityByUsername(username);
            if (!target.isCustomer()) {
                model.addAttribute("errorMsg", "只能編輯會員（顧客）的個人資料");
                return "profile/edit";
            }
            model.addAttribute("targetUsername", target.getUsername());
            model.addAttribute("targetName", target.getName());
            model.addAttribute("profile", userService.getMe(target.getUsername()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", "找不到這個會員帳號");
        }
        return "profile/edit";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("req") UpdateProfileRequest req,
                         BindingResult bindingResult,
                         @RequestParam String targetUsername,
                         HttpServletRequest request,
                         Model model) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) return "redirect:/auth/login?redirect=/my-profile";

        // 安全防呆：會員只能編輯自己的帳號，不管表單隱藏欄位傳了什麼都強制鎖定成本人
        String effectiveTarget = loginUser.isCustomer() ? loginUser.getUsername() : targetUsername;

        model.addAttribute("user", loginUser);
        model.addAttribute("sources", CustomerSource.values());
        model.addAttribute("isStaffMode", loginUser.isStaffOrAdmin());
        model.addAttribute("targetUsername", effectiveTarget);

        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", userService.getMe(effectiveTarget));
            model.addAttribute("errorMsg", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "profile/edit";
        }

        try {
            if (loginUser.isStaffOrAdmin()) {
                var target = userService.getUserEntityByUsername(effectiveTarget);
                if (!target.isCustomer()) {
                    model.addAttribute("errorMsg", "只能編輯會員（顧客）的個人資料");
                    return "profile/edit";
                }
                model.addAttribute("targetName", target.getName());
            }

            var updated = userService.updateProfile(effectiveTarget, req);
            operationLogService.log(loginUser, "CUSTOMER", "UPDATE_PROFILE",
                    "會員 " + effectiveTarget + " 更新個人資料"
                            + (loginUser.isStaffOrAdmin() ? "（店家代填）" : ""), null);
            model.addAttribute("profile", updated);
            model.addAttribute("successMsg", "資料已更新");
        } catch (IllegalArgumentException e) {
            model.addAttribute("profile", userService.getMe(effectiveTarget));
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "profile/edit";
    }
}
