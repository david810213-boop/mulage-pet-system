package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.ClosedDateService;
import com.petgrooming.pet_system.service.MemberConsumptionService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.SlotCapacityService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 員工手機版：時段管理、交易紀錄（需求，2026-09-24 第八批）。
 *
 * 時段管理只做「單日」的調整（名額、限定物種、這天公休與否），這是現場最常改的；
 * 預設時段容量範本、固定公休星期這類長期設定仍在網頁版（連結帶 desktop=1）。
 * 交易紀錄沿用網頁版同一份合併清單（MemberConsumptionService.buildCombinedRecords），
 * 手機版多了依日期區間篩選與各付款方式小計。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileScheduleController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final UserService userService;
    private final AppointmentService appointmentService;
    private final SlotCapacityService slotCapacityService;
    private final ClosedDateService closedDateService;
    private final PaymentService paymentService;
    private final WalkInOrderRepository walkInOrderRepository;
    private final MemberConsumptionService memberConsumptionService;
    private final OperationLogService operationLogService;

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

    // ── 時段管理（單日）────────────────────────────────────────────────
    @GetMapping("/slots")
    public String slots(HttpServletRequest request, Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        LocalDate today = LocalDate.now();
        LocalDate d = date == null ? today : date;

        boolean weeklyClosed = closedDateService.getWeeklyClosureSetting().isClosedOn(d.getDayOfWeek());
        var specific = closedDateService.listUpcoming().stream()
                .filter(c -> c.getDate().isEqual(d)).findFirst().orElse(null);

        List<Map<String, Object>> slots = new ArrayList<>();
        int totalCap = 0;
        int totalBooked = 0;
        for (TimeSlotResponse s : appointmentService.getAvailableSlots(d)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", s.getStartTime().format(TIME_FMT));
            m.put("iso", s.getStartTime().toString());
            m.put("booked", s.getBooked());
            m.put("capacity", s.getCapacity());
            m.put("full", s.getCapacity() > 0 && s.getBooked() >= s.getCapacity());
            m.put("zero", s.getCapacity() == 0);
            m.put("pet", s.getAllowedPetType() == null ? "" : s.getAllowedPetType());
            slots.add(m);
            totalCap += s.getCapacity();
            totalBooked += s.getBooked();
        }

        // 日期列：今天起 14 天
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate x = today.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("iso", x.toString());
            m.put("week", i == 0 ? "今天" : weekday(x.getDayOfWeek()));
            m.put("day", x.getDayOfMonth());
            m.put("on", x.isEqual(d));
            m.put("closed", closedDateService.isClosed(x));
            days.add(m);
        }

        model.addAttribute("user", user);
        model.addAttribute("date", d);
        model.addAttribute("dateLabel", d.getMonthValue() + " 月 " + d.getDayOfMonth() + " 日 週" + weekday(d.getDayOfWeek()));
        model.addAttribute("days", days);
        model.addAttribute("slots", slots);
        model.addAttribute("totalCap", totalCap);
        model.addAttribute("totalBooked", totalBooked);
        model.addAttribute("weeklyClosed", weeklyClosed);
        model.addAttribute("specificClosed", specific != null);
        model.addAttribute("closedReason", specific != null ? specific.getReason() : null);
        model.addAttribute("isPast", d.isBefore(today));
        model.addAttribute("activeTab", "more");
        return "m/slots";
    }

    @PostMapping("/slots/update")
    public String updateSlot(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam int capacity,
            @RequestParam(required = false) String allowedPetType) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            PetType pet = parsePetType(allowedPetType);
            slotCapacityService.setCapacity(date, time, capacity, pet);
            operationLogService.log(user, "APPOINTMENT", "SET_SLOT_CAPACITY", date + " " + time,
                    "調整為 " + capacity + " 位" + (pet != null ? "，僅限" + pet.getDescription() : "") + "（手機版）");
            ra.addFlashAttribute("toast", time.format(TIME_FMT) + " 已改成 " + capacity + " 位"
                    + (pet != null ? "，僅限" + pet.getDescription() : ""));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "調整失敗：" + e.getMessage());
        }
        return "redirect:/m/slots?date=" + date;
    }

    @PostMapping("/slots/closed")
    public String setClosed(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean closed,
            @RequestParam(required = false) String reason) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (closed) {
            String r = reason == null || reason.isBlank() ? null : reason.trim();
            closedDateService.setClosed(date, r);
            operationLogService.log(user, "APPOINTMENT", "SET_CLOSED_DATE", date.toString(),
                    (r == null ? "公休" : r) + "（手機版）");
            ra.addFlashAttribute("toast", date.getMonthValue() + "/" + date.getDayOfMonth() + " 已設為公休");
        } else {
            closedDateService.removeClosed(date);
            operationLogService.log(user, "APPOINTMENT", "UNSET_CLOSED_DATE", date.toString(), "取消公休（手機版）");
            ra.addFlashAttribute("toast", date.getMonthValue() + "/" + date.getDayOfMonth() + " 已取消公休");
        }
        return "redirect:/m/slots?date=" + date;
    }

    // ── 交易紀錄 ────────────────────────────────────────────────────────
    // r：today 今天（預設）/ yesterday 昨天 / week 本週 / month 本月 / last30 近 30 天
    @GetMapping("/transactions")
    public String transactions(HttpServletRequest request, Model model,
            @RequestParam(defaultValue = "today") String r) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        LocalDate today = LocalDate.now();
        LocalDate from;
        LocalDate to = today;
        switch (r) {
            case "yesterday" -> { from = today.minusDays(1); to = from; }
            case "week" -> from = today.with(DayOfWeek.MONDAY);
            case "month" -> from = today.withDayOfMonth(1);
            case "last30" -> from = today.minusDays(29);
            default -> from = today;
        }
        final LocalDate f = from;
        final LocalDate t = to;

        List<ConsumptionRecordResponse> all = memberConsumptionService.buildCombinedRecords(
                paymentService.getAllTransactions(), walkInOrderRepository.findAllByOrderByCreatedAtDesc());
        List<ConsumptionRecordResponse> picked = all.stream()
                .filter(x -> x.getTime() != null)
                .filter(x -> !x.getTime().toLocalDate().isBefore(f) && !x.getTime().toLocalDate().isAfter(t))
                .toList();

        Map<String, Integer> byMethod = new LinkedHashMap<>();
        for (ConsumptionRecordResponse x : picked) {
            byMethod.merge(x.getPaymentMethodLabel() == null ? "—" : x.getPaymentMethodLabel(), x.getAmount(), Integer::sum);
        }
        List<Map<String, Object>> rows = picked.stream().limit(200).<Map<String, Object>>map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", x.getTime().format(DT_FMT));
            m.put("code", x.getCode());
            m.put("source", x.getSourceLabel());
            m.put("petName", x.getPetName() == null || x.getPetName().isBlank() ? "（未填毛孩）" : x.getPetName());
            m.put("method", x.getPaymentMethodLabel());
            m.put("handledBy", x.getHandledBy());
            m.put("amount", x.getAmount());
            m.put("url", x.getRecordId() == null ? null
                    : "APPOINTMENT".equals(x.getSourceType()) ? "/m/appointments/" + x.getRecordId()
                    : "/m/walk-in/" + x.getRecordId());
            return m;
        }).toList();

        model.addAttribute("user", user);
        model.addAttribute("r", r);
        model.addAttribute("rangeLabel", f.isEqual(t) ? f.getMonthValue() + "/" + f.getDayOfMonth()
                : f.getMonthValue() + "/" + f.getDayOfMonth() + " – " + t.getMonthValue() + "/" + t.getDayOfMonth());
        model.addAttribute("rows", rows);
        model.addAttribute("count", picked.size());
        model.addAttribute("total", picked.stream().mapToInt(ConsumptionRecordResponse::getAmount).sum());
        model.addAttribute("byMethod", byMethod);
        model.addAttribute("activeTab", "more");
        return "m/transactions";
    }

    // ────────────────────────────────────────────────────────────────────

    private PetType parsePetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PetType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String weekday(DayOfWeek d) {
        String[] w = { "一", "二", "三", "四", "五", "六", "日" };
        return w[d.getValue() - 1];
    }
}
