package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.MobilePendingOperatorGroup;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PendingOperatorMatrixService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 員工手機版：待補經手人、儲值審核（需求，2026-09-24 第六批）。
 *
 * 待補經手人：預約單跟現場單合在同一頁，一張單一張卡片、一個項目一列選經手人，
 * 按一次「儲存」全部寫入（跟網頁版矩陣表單的「統一儲存」同樣概念），
 * 分別呼叫既有的 AppointmentService.fillItemOperator() / WalkInOrderService.fillOperator()。
 *
 * 儲值審核：顧客在 LINE 送出的轉帳儲值申請，確認入帳或駁回，
 * 呼叫既有的 TopUpService.confirm() / reject()。店家手動加值仍在網頁版（有 CSRF 保護）。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileOpsController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final UserService userService;
    private final PendingOperatorMatrixService pendingOperatorMatrixService;
    private final AppointmentService appointmentService;
    private final WalkInOrderService walkInOrderService;
    private final TopUpService topUpService;
    private final OperationLogService operationLogService;
    private final MobileViewHelper view;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) {
            return null;
        }
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── 待補經手人 ──────────────────────────────────────────────────────
    @GetMapping("/operators")
    public String operators(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        List<MobilePendingOperatorGroup> groups = pendingOperatorMatrixService.buildMobileGroups();
        model.addAttribute("user", user);
        model.addAttribute("groups", groups);
        model.addAttribute("lineCount", groups.stream().mapToInt(g -> g.getLines().size()).sum());
        model.addAttribute("dateFmt", DATE_FMT);
        model.addAttribute("staffOptions", view.staffOptions());
        model.addAttribute("activeTab", "more");
        return "m/operators";
    }

    // kinds、itemIds、staffIds 三個平行陣列，一個項目一組；staffId 空白就跳過（維持待補）
    @PostMapping("/operators")
    public String saveOperators(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(required = false) List<Long> itemIds,
            @RequestParam(required = false) List<String> staffIds) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (kinds == null || itemIds == null || staffIds == null
                || kinds.size() != itemIds.size() || itemIds.size() != staffIds.size()) {
            ra.addFlashAttribute("toastError", "資料不完整，請重新整理後再試一次");
            return "redirect:/m/operators";
        }
        int filled = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < itemIds.size(); i++) {
            String staffIdStr = staffIds.get(i);
            if (staffIdStr == null || staffIdStr.isBlank()) {
                continue;
            }
            Long itemId = itemIds.get(i);
            Long staffId = Long.valueOf(staffIdStr);
            try {
                if ("A".equals(kinds.get(i))) {
                    appointmentService.fillItemOperator(itemId, staffId);
                    operationLogService.log(user, "APPOINTMENT", "FILL_OPERATOR",
                            "項目 #" + itemId, "指定經手人 #" + staffId + "（手機版）");
                } else {
                    walkInOrderService.fillOperator(itemId, staffId);
                    operationLogService.log(user, "WALKIN", "FILL_OPERATOR",
                            "項目 #" + itemId, "指定經手人 #" + staffId + "（手機版）");
                }
                filled++;
            } catch (IllegalArgumentException | IllegalStateException e) {
                errors.add("項目 #" + itemId + "：" + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("toastError", "有 " + errors.size() + " 筆沒存成功（" + errors.get(0) + "）");
        } else if (filled > 0) {
            ra.addFlashAttribute("toast", "已補填 " + filled + " 筆經手人");
        } else {
            ra.addFlashAttribute("toastError", "還沒選任何經手人");
        }
        return "redirect:/m/operators";
    }

    // ── 儲值審核 ────────────────────────────────────────────────────────
    @GetMapping("/topups")
    public String topups(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        List<TopUpRequestResponse> pending = topUpService.pending();
        model.addAttribute("user", user);
        model.addAttribute("requests", pending);
        model.addAttribute("totalAmount", pending.stream()
                .mapToInt(r -> r.getAmount() == null ? 0 : r.getAmount()).sum());
        model.addAttribute("dateTimeFmt", DATE_TIME_FMT);
        model.addAttribute("activeTab", "more");
        return "m/topups";
    }

    @PostMapping("/topups/{id}/confirm")
    public String confirmTopup(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            var result = topUpService.confirm(id, user.getName());
            operationLogService.log(user, "WALLET", "TOPUP_CONFIRM",
                    "會員 " + result.getName(), "+$" + result.getAmount() + "（手機版）");
            ra.addFlashAttribute("toast", "已入帳：" + result.getName() + " +" + result.getAmount());
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "確認失敗：" + e.getMessage());
        }
        return "redirect:/m/topups";
    }

    @PostMapping("/topups/{id}/reject")
    public String rejectTopup(@PathVariable Long id, @RequestParam(required = false) String reason,
            HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        String r = reason == null ? null : reason.trim();
        try {
            topUpService.reject(id, user.getName(), r == null || r.isEmpty() ? null : r);
            operationLogService.log(user, "WALLET", "TOPUP_REJECT", "儲值申請 #" + id,
                    (r == null ? "" : r) + "（手機版）");
            ra.addFlashAttribute("toast", "已駁回這筆申請");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "駁回失敗：" + e.getMessage());
        }
        return "redirect:/m/topups";
    }
}
