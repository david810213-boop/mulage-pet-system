package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.export.ExcelSupport;
import com.petgrooming.pet_system.export.StaffBreakdownExcel;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PerformanceService;
import com.petgrooming.pet_system.service.BonusTierService;
import com.petgrooming.pet_system.service.PerformanceSplitService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin/performance")
@RequiredArgsConstructor
public class PerformanceMvcController {

    private final PerformanceService performanceService;
    private final BonusTierService bonusTierService;
    private final PerformanceSplitService performanceSplitService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    // ── GET /admin/performance ─────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String daily(Model model,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @CurrentUser User loginUser) {
        if (date == null) date = LocalDate.now();
        model.addAttribute("user", loginUser);
        model.addAttribute("date", date);
        // 當日績效紀錄排除已拆分過的（只在拆分歷史顯示，避免同一筆看起來出現兩次）
        model.addAttribute("records", performanceSplitService.getDailyRecordsForDisplay(date));
        model.addAttribute("staffList", userService.getAllStaffEntities());
        model.addAttribute("categories", Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER).toList());
        // 拆分區塊直接顯示完整清單（含寵物名/服務項目）＋當日拆分歷史，不用跳頁
        model.addAttribute("candidates", performanceSplitService.getSplitCandidates(date, date, null, null));
        model.addAttribute("splitHistory", performanceSplitService.getSplitHistory(date, date, null, null));
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
                              @RequestParam(required = false) String returnTo,
                              RedirectAttributes ra,
                              @CurrentUser User user) {
        try {
            performanceSplitService.splitRecord(sourceRecordId, toStaffId, note);
            operationLogService.log(user, "PERFORMANCE", "SPLIT_POINTS",
                    "績效紀錄 #" + sourceRecordId, "拆分給員工 #" + toStaffId + (note != null ? "：" + note : ""));
            ra.addFlashAttribute("successMsg", "積分已對半拆分成功");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "拆分失敗：" + e.getMessage());
        }
        if (returnTo != null && !returnTo.isBlank()) {
            return "redirect:" + returnTo;
        }
        return "redirect:/admin/performance" + (date != null ? "?date=" + date : "");
    }

    // ── GET /admin/performance/monthly ────────────────────────────────────
    // 月報：所有員工月度積分排行
    @RequireRole({UserRole.ADMIN})
    @GetMapping("/monthly")
    public String monthly(Model model,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month,
                          @CurrentUser User loginUser) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        model.addAttribute("user", loginUser);
        model.addAttribute("ym", ym);
        model.addAttribute("ranking", performanceService.getLiveMonthlyRanking(ym.getYear(), ym.getMonthValue()));
        model.addAttribute("tiers", bonusTierService.getAllBonusTiers());
        return "admin/performance-monthly";
    }

    // ── POST /admin/performance/settle ────────────────────────────────────
    // 月底結算（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/settle")
    public String settle(@RequestParam int year, @RequestParam int month, RedirectAttributes ra, @CurrentUser User user) {
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
                              @RequestParam(required = false) Integer month, Model model,
                              @CurrentUser User loginUser) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month) : YearMonth.now();

        model.addAttribute("user", loginUser);
        model.addAttribute("staff", userService.getUserEntityById(staffId));
        model.addAttribute("ym", ym);
        model.addAttribute("records", performanceService.getMonthlyRecords(staffId, ym.getYear(), ym.getMonthValue()));
        model.addAttribute("history", performanceService.getStaffHistory(staffId));
        model.addAttribute("breakdown",
                performanceService.getStaffMonthlyBreakdown(staffId, ym.getYear(), ym.getMonthValue()));
        model.addAttribute("categories", Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER).toList());
        return "admin/performance-staff";
    }

    // ── POST /admin/performance/cancel-settle ────────────────────────────
    // 取消結算：僅限「當月」，避免不小心取消掉過去已對外發放獎金的月份
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/cancel-settle")
    public String cancelSettle(@RequestParam int year, @RequestParam int month, RedirectAttributes ra, @CurrentUser User user) {
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
    public String myProgress(Model model, @CurrentUser User user) {
        model.addAttribute("user", user);
        model.addAttribute("progress",
                performanceService.getMyProgress(user.getId(), LocalDate.now()));
        model.addAttribute("myRecordsToday",
                performanceService.getDailyRecords(LocalDate.now()).stream()
                        .filter(r -> r.getStaff().getId().equals(user.getId()))
                        .toList());
        return "admin/performance-my";
    }

    // ── GET /admin/performance/bonus-tiers ──────────────────────────────
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/bonus-tiers")
    public String bonusTiers(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("tiers", bonusTierService.getAllBonusTiers());
        return "admin/performance-bonus-tiers";
    }

    // ── POST /admin/performance/bonus-tiers/create ──────────────────────
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bonus-tiers/create")
    public String createBonusTier(@RequestParam int minPoints, @RequestParam int maxPoints,
                                  @RequestParam int bonusAmount, RedirectAttributes ra,
                                  @CurrentUser User user) {
        try {
            bonusTierService.createBonusTier(minPoints, maxPoints, bonusAmount);
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
                                  @RequestParam int maxPoints, @RequestParam int bonusAmount, RedirectAttributes ra,
                                  @CurrentUser User user) {
        try {
            bonusTierService.updateBonusTier(id, minPoints, maxPoints, bonusAmount);
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
    public String deleteBonusTier(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        bonusTierService.deleteBonusTier(id);
        operationLogService.log(user, "PERFORMANCE", "DELETE_BONUS_TIER", "級距 #" + id, null);
        ra.addFlashAttribute("successMsg", "已刪除級距");
        return "redirect:/admin/performance/bonus-tiers";
    }

    // ── GET /admin/performance/staff-breakdown ───────────────────────────
    // 積分項目統計矩陣：全部員工 × 全部積分項目，一次看完
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/staff-breakdown")
    public String staffBreakdown(Model model,
                                 @RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer month,
                                 @CurrentUser User loginUser) {
        YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();

        model.addAttribute("user", loginUser);
        model.addAttribute("ym", ym);
        model.addAttribute("matrix", performanceService.getMonthlyMatrix(ym.getYear(), ym.getMonthValue()));
        return "admin/performance-staff-breakdown";
    }

    // ── GET /admin/performance/staff-breakdown/export ────────────────────
    // 匯出上面那份矩陣統計成 Excel（總計/隻數、換算積分 兩個區塊）
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/staff-breakdown/export")
    public void exportStaffBreakdown(HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam int year, @RequestParam int month) throws IOException {
        var matrix = performanceService.getMonthlyMatrix(year, month);
        try (var workbook = StaffBreakdownExcel.build(year, month, matrix)) {
            ExcelSupport.writeXlsx(response, year + "年" + month + "月積分項目統計.xlsx", workbook);
        }
    }

    // ── GET /admin/performance/split-manage ──────────────────────────────
    // 拆分管理頁面——強化版待拆分清單（含寵物名/服務項目/篩選）
    // ＋拆分歷史查詢（誰在何時把哪隻寵物的哪個項目拆給了誰）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/split-manage")
    public String splitManage(Model model,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                              @RequestParam(required = false) String petName,
                              @RequestParam(required = false) Long staffId,
                              @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("staffList", userService.getAllStaffEntities());
        model.addAttribute("dateFrom", dateFrom != null ? dateFrom : LocalDate.now());
        model.addAttribute("dateTo", dateTo != null ? dateTo : LocalDate.now());
        model.addAttribute("petName", petName);
        model.addAttribute("staffId", staffId);
        model.addAttribute("candidates",
                performanceSplitService.getSplitCandidates(dateFrom, dateTo, petName, staffId));
        model.addAttribute("history",
                performanceSplitService.getSplitHistory(dateFrom, dateTo, petName, staffId));
        return "admin/performance-split-manage";
    }
}
