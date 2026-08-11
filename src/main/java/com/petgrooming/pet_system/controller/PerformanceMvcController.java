package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PerformanceService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin/performance")
@RequiredArgsConstructor
public class PerformanceMvcController {

    private final PerformanceService performanceService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /admin/performance ─────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String daily(HttpServletRequest request, Model model,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("date", date);
        model.addAttribute("records", performanceService.getDailyRecords(date));
        model.addAttribute("staffList", userService.getAllStaffEntities());
        // 供拆分表單選擇「要拆分哪一筆原始績效紀錄」（只能拆分積分 > 0 的紀錄）
        model.addAttribute("splittableRecords", performanceService.getDailyRecords(date).stream()
                .filter(r -> r.getPoints() != null && r.getPoints() > 0)
                .toList());
        model.addAttribute("categories", Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER).toList());
        return "admin/performance-daily";
    }

    // ── POST /admin/performance/split ────────────────────────────────────
    // 拆分績效：從指定的原始紀錄「對半平分」給另一位員工
    // 僅支援對半拆分（不接受手動輸入任意積分），避免長期累積浮點數誤差。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/split")
    public String splitRecord(@RequestParam Long sourceRecordId,
                              @RequestParam Long toStaffId,
                              @RequestParam(required = false) String note,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            performanceService.splitRecord(sourceRecordId, toStaffId, note);
            operationLogService.log(user, "PERFORMANCE", "SPLIT_POINTS",
                    "績效紀錄 #" + sourceRecordId, "拆分給員工 #" + toStaffId + (note != null ? "：" + note : ""));
            ra.addFlashAttribute("successMsg", "積分已對半拆分成功");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "拆分失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance" + (date != null ? "?date=" + date : "");
    }

    // ── GET /admin/performance/monthly ────────────────────────────────────
    // 月報：所有員工月度積分排行
    @RequireRole({UserRole.ADMIN})
    @GetMapping("/monthly")
    public String monthly(HttpServletRequest request, Model model,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("ym", ym);
        model.addAttribute("ranking", performanceService.getLiveMonthlyRanking(ym.getYear(), ym.getMonthValue()));
        model.addAttribute("tiers", performanceService.getAllBonusTiers());
        return "admin/performance-monthly";
    }

    // ── POST /admin/performance/settle ────────────────────────────────────
    // 月底結算（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/settle")
    public String settle(@RequestParam int year, @RequestParam int month,
                         HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            List<MonthlyPerformance> results = performanceService.settleMonth(year, month);
            operationLogService.log(user, "PERFORMANCE", "SETTLE",
                    year + "年" + month + "月", "共 " + results.size() + " 位員工");
            ra.addFlashAttribute("successMsg",
                    year + "年" + month + "月結算完成，共 " + results.size() + " 位員工");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "結算失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance/monthly?year=" + year + "&month=" + month;
    }

    // ── GET /admin/performance/staff/{staffId} ─────────────────────────────
    // 某員工的本月績效明細
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/staff/{staffId}")
    public String staffDetail(@PathVariable Long staffId,
                              @RequestParam(required = false) Integer year,
                              @RequestParam(required = false) Integer month,
                              HttpServletRequest request, Model model) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month) : YearMonth.now();

        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("staff", userService.getUserEntityById(staffId));
        model.addAttribute("ym", ym);
        model.addAttribute("records", performanceService.getMonthlyRecords(staffId, ym.getYear(), ym.getMonthValue()));
        model.addAttribute("history", performanceService.getStaffHistory(staffId));
        return "admin/performance-staff";
    }

    // ── POST /admin/performance/cancel-settle ────────────────────────────
    // 取消結算：僅限「當月」，避免不小心取消掉過去已對外發放獎金的月份
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/cancel-settle")
    public String cancelSettle(@RequestParam int year, @RequestParam int month,
                               HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            performanceService.cancelSettlement(year, month);
            operationLogService.log(user, "PERFORMANCE", "CANCEL_SETTLE", year + "年" + month + "月", null);
            ra.addFlashAttribute("successMsg", "已取消 " + year + "年" + month + "月 的結算");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "取消失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance/monthly?year=" + year + "&month=" + month;
    }

    // ── GET /admin/performance/my ────────────────────────────────────────
    // 我的績效：員工查詢自己今日/當月累積積分，以及距離下一個獎勵金級距還差幾分
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/my")
    public String myProgress(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        model.addAttribute("progress",
                performanceService.getMyProgress(user.getId(), java.time.LocalDate.now()));
        model.addAttribute("myRecordsToday",
                performanceService.getDailyRecords(java.time.LocalDate.now()).stream()
                        .filter(r -> r.getStaff().getId().equals(user.getId()))
                        .toList());
        return "admin/performance-my";
    }

    // ── GET /admin/performance/bonus-tiers ──────────────────────────────
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/bonus-tiers")
    public String bonusTiers(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("tiers", performanceService.getAllBonusTiers());
        return "admin/performance-bonus-tiers";
    }

    // ── POST /admin/performance/bonus-tiers/create ──────────────────────
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bonus-tiers/create")
    public String createBonusTier(@RequestParam int minPoints, @RequestParam int maxPoints,
                                  @RequestParam int bonusAmount,
                                  HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            performanceService.createBonusTier(minPoints, maxPoints, bonusAmount);
            operationLogService.log(user, "PERFORMANCE", "CREATE_BONUS_TIER",
                    minPoints + "-" + maxPoints + "分", "$" + bonusAmount);
            ra.addFlashAttribute("successMsg", "已新增級距");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "新增失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance/bonus-tiers";
    }

    // ── POST /admin/performance/bonus-tiers/{id}/update ─────────────────
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bonus-tiers/{id}/update")
    public String updateBonusTier(@PathVariable Long id, @RequestParam int minPoints,
                                  @RequestParam int maxPoints, @RequestParam int bonusAmount,
                                  HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            performanceService.updateBonusTier(id, minPoints, maxPoints, bonusAmount);
            operationLogService.log(user, "PERFORMANCE", "UPDATE_BONUS_TIER",
                    "級距 #" + id, minPoints + "-" + maxPoints + "分 → $" + bonusAmount);
            ra.addFlashAttribute("successMsg", "已更新級距");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance/bonus-tiers";
    }

    // ── POST /admin/performance/bonus-tiers/{id}/delete ─────────────────
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bonus-tiers/{id}/delete")
    public String deleteBonusTier(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        performanceService.deleteBonusTier(id);
        operationLogService.log(user, "PERFORMANCE", "DELETE_BONUS_TIER", "級距 #" + id, null);
        ra.addFlashAttribute("successMsg", "已刪除級距");
        return "redirect:/admin/performance/bonus-tiers";
    }
}
