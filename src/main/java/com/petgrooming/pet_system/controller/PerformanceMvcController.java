package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
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
    private final AppointmentRepository appointmentRepository;

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
        // 今日已結帳預約（供拆分表單的下拉選單）
        model.addAttribute("paidAppointments", appointmentRepository.findByDateAndPaidTrue(date));
        model.addAttribute("categories", Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER).toList());
        return "admin/performance-daily";
    }

    // ── POST /admin/performance/add ────────────────────────────────────────
    // 新增一筆績效紀錄
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/add")
    public String addRecord(@RequestParam Long staffId,
                            @RequestParam Long appointmentId,
                            @RequestParam PerformanceCategory category,
                            @RequestParam Double points,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
                            @RequestParam(required = false) String note,
                            RedirectAttributes ra) {
        try {
            performanceService.addRecord(staffId, appointmentId, category, points, serviceDate, note);
            ra.addFlashAttribute("successMsg", "績效紀錄新增成功");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "新增失敗：" + e.getMessage());
        }
        return "redirect:/admin/performance?date=" + serviceDate;
    }

    // ── GET /admin/performance/monthly ────────────────────────────────────
    // 月報：所有員工月度積分排行
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/monthly")
    public String monthly(HttpServletRequest request, Model model,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("ym", ym);
        model.addAttribute("ranking", performanceService.getMonthlyRanking(ym.getYear(), ym.getMonthValue()));
        return "admin/performance-monthly";
    }

    // ── POST /admin/performance/settle ────────────────────────────────────
    // 月底結算（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/settle")
    public String settle(@RequestParam int year, @RequestParam int month, RedirectAttributes ra) {
        try {
            List<MonthlyPerformance> results = performanceService.settleMonth(year, month);
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
}
