package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.assistant.AssistantReviewService;
import com.petgrooming.pet_system.service.assistant.AssistantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 後台「慕馬對話紀錄」：店長抽查回答內容、看本月花費，開放前用「試問慕馬」以會員身分測試。 */
@Controller
@RequestMapping("/admin/assistant-logs")
@RequiredArgsConstructor
public class AssistantLogMvcController {

    private final AssistantReviewService reviewService;
    private final AssistantService assistantService;
    private final UserService userService;

    @RequireRole(UserRole.ADMIN)
    @GetMapping
    public String list(Model model, @CurrentUser User user) {
        fill(model, user);
        return "admin/assistant-logs";
    }

    /** 先用姓名或電話找會員：剛好一位就直接問，多位就列出來讓店長選。 */
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/trial")
    public String trial(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) Long memberId,
                        @RequestParam String message,
                        Model model, @CurrentUser User user) {
        model.addAttribute("trialKeyword", keyword);
        model.addAttribute("trialMessage", message);
        try {
            User member;
            if (memberId != null) {
                member = userService.getUserEntityById(memberId);
            } else {
                List<User> found = userService.searchCustomers(keyword);
                if (found.isEmpty()) {
                    throw new IllegalArgumentException("找不到符合「" + keyword + "」的會員");
                }
                if (found.size() > 1) {
                    model.addAttribute("candidates", found);
                    fill(model, user);
                    return "admin/assistant-logs";
                }
                member = found.get(0);
            }
            model.addAttribute("trial", assistantService.trial(member, message));
        } catch (IllegalArgumentException e) {
            model.addAttribute("trialError", e.getMessage());
        }
        fill(model, user);
        return "admin/assistant-logs";
    }

    private void fill(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("usage", reviewService.usage());
        model.addAttribute("logs", reviewService.recentLogs());
    }
}
