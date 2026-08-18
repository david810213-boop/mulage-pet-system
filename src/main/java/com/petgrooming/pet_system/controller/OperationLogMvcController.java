package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 後台「操作紀錄」頁面：僅 ADMIN 可查看，記錄各項關鍵操作（保留 60 天）
@Controller
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogMvcController {

    private final OperationLogService operationLogService;
    private final UserService userService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    @RequireRole(UserRole.ADMIN)
    @GetMapping
    public String list(HttpServletRequest request, Model model,
                       @RequestParam(required = false) String category) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("logs",
                (category == null || category.isBlank())
                        ? operationLogService.getAll()
                        : operationLogService.getByCategory(category));
        model.addAttribute("category", category);
        return "admin/operation-logs";
    }
}
