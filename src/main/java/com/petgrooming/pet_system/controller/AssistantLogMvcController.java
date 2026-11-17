package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.assistant.AssistantReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 後台「慕馬對話紀錄」：店長抽查回答內容、看本月花費。 */
@Controller
@RequestMapping("/admin/assistant-logs")
@RequiredArgsConstructor
public class AssistantLogMvcController {

    private final AssistantReviewService reviewService;

    @RequireRole(UserRole.ADMIN)
    @GetMapping
    public String list(Model model, @CurrentUser User user) {
        model.addAttribute("user", user);
        model.addAttribute("usage", reviewService.usage());
        model.addAttribute("logs", reviewService.recentLogs());
        return "admin/assistant-logs";
    }
}
