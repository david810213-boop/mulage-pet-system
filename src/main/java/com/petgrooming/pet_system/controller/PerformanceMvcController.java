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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
        // 需求 12：當日績效紀錄排除已拆分過的（只在拆分歷史顯示，避免同一筆看起來出現兩次）
        model.addAttribute("records", performanceService.getDailyRecordsForDisplay(date));
        model.addAttribute("staffList", userService.getAllStaffEntities());
        model.addAttribute("categories", Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER).toList());
        // 需求 12：拆分區塊直接顯示完整清單（含寵物名/服務項目）＋當日拆分歷史，不用跳頁
        model.addAttribute("candidates", performanceService.getSplitCandidates(date, date, null, null));
        model.addAttribute("splitHistory", performanceService.getSplitHistory(date, date, null, null));
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
        if (returnTo != null && !returnTo.isBlank()) {
            return "redirect:" + returnTo;
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

    // ── GET /admin/performance/staff-breakdown ───────────────────────────
    // 積分項目統計矩陣：全部員工 × 全部積分項目，一次看完
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/staff-breakdown")
    public String staffBreakdown(HttpServletRequest request, Model model,
                                 @RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer month) {
        YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();

        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("ym", ym);
        model.addAttribute("matrix", performanceService.getMonthlyMatrix(ym.getYear(), ym.getMonthValue()));
        return "admin/performance-staff-breakdown";
    }

    // ── GET /admin/performance/staff-breakdown/export ────────────────────
    // 匯出上面那份矩陣統計成 Excel（總計/隻數、換算積分 兩個區塊）
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/staff-breakdown/export")
    public void exportStaffBreakdown(HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam int year, @RequestParam int month) throws java.io.IOException {
        var matrix = performanceService.getMonthlyMatrix(year, month);
        var categories = matrix.getCategories();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("積分項目統計");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;

            // 區塊一：總計/隻數
            Row titleRow1 = sheet.createRow(rowIdx++);
            titleRow1.createCell(0).setCellValue(year + "年" + month + "月　總計/隻數");

            Row header1 = sheet.createRow(rowIdx++);
            header1.createCell(0).setCellValue("人");
            for (int i = 0; i < categories.size(); i++) {
                Cell c = header1.createCell(i + 1);
                c.setCellValue(categories.get(i).getLabel());
                c.setCellStyle(headerStyle);
            }

            for (var r : matrix.getRows()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getStaffName());
                for (int i = 0; i < categories.size(); i++) {
                    row.createCell(i + 1).setCellValue(r.getCountByCategory().get(categories.get(i)));
                }
            }
            Row totalsCountRow = sheet.createRow(rowIdx++);
            totalsCountRow.createCell(0).setCellValue("合計");
            for (int i = 0; i < categories.size(); i++) {
                totalsCountRow.createCell(i + 1).setCellValue(matrix.getTotalsRow().getCountByCategory().get(categories.get(i)));
            }

            rowIdx++; // 空一行

            // 區塊二：換算積分
            Row titleRow2 = sheet.createRow(rowIdx++);
            titleRow2.createCell(0).setCellValue(year + "年" + month + "月　換算積分");

            Row header2 = sheet.createRow(rowIdx++);
            header2.createCell(0).setCellValue("人");
            for (int i = 0; i < categories.size(); i++) {
                Cell c = header2.createCell(i + 1);
                c.setCellValue(categories.get(i).getLabel());
                c.setCellStyle(headerStyle);
            }
            Cell totalHeaderCell = header2.createCell(categories.size() + 1);
            totalHeaderCell.setCellValue("總分");
            totalHeaderCell.setCellStyle(headerStyle);

            for (var r : matrix.getRows()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getStaffName());
                for (int i = 0; i < categories.size(); i++) {
                    row.createCell(i + 1).setCellValue(r.getPointsByCategory().get(categories.get(i)));
                }
                row.createCell(categories.size() + 1).setCellValue(r.getTotalPoints());
            }
            Row totalsPointsRow = sheet.createRow(rowIdx++);
            totalsPointsRow.createCell(0).setCellValue("合計");
            for (int i = 0; i < categories.size(); i++) {
                totalsPointsRow.createCell(i + 1).setCellValue(matrix.getTotalsRow().getPointsByCategory().get(categories.get(i)));
            }
            totalsPointsRow.createCell(categories.size() + 1).setCellValue(matrix.getGrandTotal());

            for (int i = 0; i <= categories.size() + 1; i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = year + "年" + month + "月積分項目統計.xlsx";
            String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);

            workbook.write(response.getOutputStream());
        }
    }

    // ── GET /admin/performance/split-manage ──────────────────────────────
    // 需求 12：拆分管理頁面——強化版待拆分清單（含寵物名/服務項目/篩選）
    // ＋拆分歷史查詢（誰在何時把哪隻寵物的哪個項目拆給了誰）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/split-manage")
    public String splitManage(HttpServletRequest request, Model model,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                              @RequestParam(required = false) String petName,
                              @RequestParam(required = false) Long staffId) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("staffList", userService.getAllStaffEntities());
        model.addAttribute("dateFrom", dateFrom != null ? dateFrom : LocalDate.now());
        model.addAttribute("dateTo", dateTo != null ? dateTo : LocalDate.now());
        model.addAttribute("petName", petName);
        model.addAttribute("staffId", staffId);
        model.addAttribute("candidates",
                performanceService.getSplitCandidates(dateFrom, dateTo, petName, staffId));
        model.addAttribute("history",
                performanceService.getSplitHistory(dateFrom, dateTo, petName, staffId));
        return "admin/performance-split-manage";
    }
}
