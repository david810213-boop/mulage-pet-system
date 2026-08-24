package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.MemberImportService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 需求（追加，2026-08-23）：店家轉型，既有會員資料批次匯入後台頁面。
 */
@Controller
@RequestMapping("/admin/member-import")
@RequiredArgsConstructor
public class MemberImportMvcController {

    private final MemberImportService memberImportService;
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

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String page(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        return "admin/member-import";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/upload")
    public String upload(HttpServletRequest request, @RequestParam MultipartFile file, Model model) {
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        if (file.isEmpty()) {
            model.addAttribute("errorMsg", "請選擇檔案");
            return "admin/member-import";
        }
        try {
            var result = memberImportService.importFromCsv(file);
            model.addAttribute("result", result);
            operationLogService.log(user, "CUSTOMER", "IMPORT_MEMBERS",
                    "批次匯入會員資料", "新建 " + result.getMembersCreated() + " 筆會員、"
                            + result.getPetsCreated() + " 隻寵物，跳過 " + result.getMembersSkipped() + " 筆");
        } catch (Exception e) {
            model.addAttribute("errorMsg", "匯入失敗：" + e.getMessage());
        }
        return "admin/member-import";
    }
}
