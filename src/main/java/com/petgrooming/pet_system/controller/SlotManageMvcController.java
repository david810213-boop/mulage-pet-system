package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.ClosedDateService;
import com.petgrooming.pet_system.service.DefaultSlotCapacityTemplateService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.SlotCapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 時段管理（需求 1、16）：某一天各時段的名額上限、預設時段容量範本、每週固定公休與指定公休日。
 */
@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class SlotManageMvcController {

    private final AppointmentQueryService appointmentQueryService;
    private final OperationLogService operationLogService;
    private final SlotCapacityService slotCapacityService;
    private final ClosedDateService closedDateService; // 需求 16：公休日設定
    private final DefaultSlotCapacityTemplateService defaultSlotCapacityTemplateService; // 需求（追加，2026-08-27）：預設時段容量範本

    // ── GET /appointments/slots-manage ───────────────────────────────────
    // 需求 1：時段開關管理——查看某一天每個時段的名額上限/已預約數，
    // 可手動調整上限或直接關閉（設為 0），只影響剩餘名額，不影響已預約紀錄。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/slots-manage")
    public String slotsManage(Model model,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @CurrentUser User loginUser) {
        if (date == null) date = LocalDate.now();
        model.addAttribute("user", loginUser);
        model.addAttribute("date", date);
        model.addAttribute("slots", appointmentQueryService.getAvailableSlots(date));
        // 需求 16：公休日設定
        model.addAttribute("isClosedToday", closedDateService.isClosed(date));
        model.addAttribute("upcomingClosedDates", closedDateService.listUpcoming());
        model.addAttribute("weeklyClosureSetting", closedDateService.getWeeklyClosureSetting());
        // 需求（追加，2026-08-27）：預設時段容量範本——通用一份，不分日期，
        // 跟上面「這一天的各時段名額」是分開的兩張表格。
        model.addAttribute("templateSlots", defaultSlotCapacityTemplateService.listForAdmin());
        return "appointments/slots-manage";
    }

    // ── POST /appointments/slots-manage/template/update ───────────────────
    // 需求（追加，2026-08-27）：店家調整「預設時段容量範本」某個時段的預設名額上限。
    // 只影響以後新建立的日期的初始值，不會回溯影響已經存在的日期
    // （已存在的日期要調整的話，用下面既有的「這一天各時段名額」表格覆寫）。
    // 需求（追加，2026-09-08）：一併調整這個時段預設限定的物種（空字串＝不限制）。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/template/update")
    public String updateDefaultSlotCapacityTemplate(@RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
                                     @RequestParam int capacity,
                                     @RequestParam(required = false) String allowedPetType,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     RedirectAttributes ra,
                                     @CurrentUser User user) {
        try {
            PetType parsedPetType = parsePetTypeOrNull(allowedPetType);
            defaultSlotCapacityTemplateService.setCapacity(time, capacity, parsedPetType);
            operationLogService.log(user, "APPOINTMENT", "SET_DEFAULT_SLOT_CAPACITY",
                    time.toString(), "預設範本調整為 " + capacity + " 位"
                            + (parsedPetType != null ? "，僅限" + parsedPetType.getDescription() : ""));
            ra.addFlashAttribute("successMsg",
                    time + " 的預設時段容量已調整為 " + capacity + " 位"
                            + (parsedPetType != null ? "，僅限" + parsedPetType.getDescription() + "預約" : "")
                            + "（往後新的日期會套用這個預設值）");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "調整失敗：" + e.getMessage());
        }
        return "redirect:/appointments/slots-manage" + (date != null ? "?date=" + date : "");
    }

    // 需求（追加，2026-09-08）：時段管理表單裡的物種下拉選單值轉成 PetType，
    // 空字串／null／不合法值一律當「不限制」，兩個時段管理 POST endpoint 共用。
    private PetType parsePetTypeOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PetType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── POST /appointments/slots-manage/weekly-closure ─────────────────────
    // 固定公休星期：設定每週固定哪幾天公休（跟單一天公休日的 ClosedDate 是兩套機制並存）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/weekly-closure")
    public String updateWeeklyClosure(@RequestParam(defaultValue = "false") boolean monday,
                                      @RequestParam(defaultValue = "false") boolean tuesday,
                                      @RequestParam(defaultValue = "false") boolean wednesday,
                                      @RequestParam(defaultValue = "false") boolean thursday,
                                      @RequestParam(defaultValue = "false") boolean friday,
                                      @RequestParam(defaultValue = "false") boolean saturday,
                                      @RequestParam(defaultValue = "false") boolean sunday,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      RedirectAttributes ra,
                                      @CurrentUser User user) {
        closedDateService.updateWeeklyClosureSetting(monday, tuesday, wednesday, thursday, friday, saturday, sunday);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_WEEKLY_CLOSURE", "固定公休星期設定", null);
        ra.addFlashAttribute("successMsg", "固定公休星期已更新");
        return "redirect:/appointments/slots-manage" + (date != null ? "?date=" + date : "");
    }

    // ── POST /appointments/slots-manage/closed-date/add ───────────────────
    // 需求 16：將指定日期設為公休日，當天所有時段自動不開放預約
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/closed-date/add")
    public String addClosedDate(@RequestParam
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String reason,
                                RedirectAttributes ra,
                                @CurrentUser User user) {
        closedDateService.setClosed(date, reason);
        operationLogService.log(user, "APPOINTMENT", "SET_CLOSED_DATE",
                date.toString(), reason == null || reason.isBlank() ? "公休" : reason);
        ra.addFlashAttribute("successMsg", date + " 已設定為公休日");
        return "redirect:/appointments/slots-manage?date=" + date;
    }

    // ── POST /appointments/slots-manage/closed-date/remove ────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/closed-date/remove")
    public String removeClosedDate(@RequestParam
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   RedirectAttributes ra,
                                   @CurrentUser User user) {
        closedDateService.removeClosed(date);
        operationLogService.log(user, "APPOINTMENT", "UNSET_CLOSED_DATE", date.toString(), "取消公休");
        ra.addFlashAttribute("successMsg", date + " 已取消公休設定");
        return "redirect:/appointments/slots-manage?date=" + date;
    }

    // ── POST /appointments/slots-manage/update ───────────────────────────
    // 需求（追加，2026-09-08）：一併調整這一天這個時段限定的物種（空字串＝不限制）。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/update")
    public String updateSlotCapacity(@RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
                                     @RequestParam int capacity,
                                     @RequestParam(required = false) String allowedPetType,
                                     RedirectAttributes ra,
                                     @CurrentUser User user) {
        try {
            PetType parsedPetType = parsePetTypeOrNull(allowedPetType);
            slotCapacityService.setCapacity(date, time, capacity, parsedPetType);
            operationLogService.log(user, "APPOINTMENT", "SET_SLOT_CAPACITY",
                    date + " " + time, "調整為 " + capacity + " 位"
                            + (parsedPetType != null ? "，僅限" + parsedPetType.getDescription() : ""));
            ra.addFlashAttribute("successMsg",
                    date + " " + time + " 時段名額已調整為 " + capacity + " 位"
                            + (parsedPetType != null ? "，僅限" + parsedPetType.getDescription() + "預約" : ""));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "調整失敗：" + e.getMessage());
        }
        return "redirect:/appointments/slots-manage?date=" + date;
    }
}
