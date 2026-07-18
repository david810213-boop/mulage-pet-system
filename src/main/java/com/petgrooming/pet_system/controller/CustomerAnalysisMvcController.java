package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.CustomerAnalysisService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 後台「會員資料分析」頁面：新客／回流客總覽 + 年齡、職業、居住區域、來源分布
@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
public class CustomerAnalysisMvcController {

    private final CustomerAnalysisService customerAnalysisService;
    private final UserService userService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    @RequireRole(UserRole.ADMIN)
    @GetMapping
    public String overview(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("stats", customerAnalysisService.getOverview());
        return "admin/customer-analysis";
    }
}
