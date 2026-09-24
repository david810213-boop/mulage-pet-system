package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.StoreSupply;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PerformanceService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.StoreSupplyService;
import com.petgrooming.pet_system.service.UserService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 員工手機版：我的績效、店用洗劑、零售商品庫存（需求，2026-09-24 第七批）。
 *
 * 只做現場每天會用到的動作：
 * - 我的績效：看今日／本月積分、離下一個獎勵金級距還差多少，把自己的一筆積分對半拆給協助的同事
 * - 店用洗劑：領用、進貨
 * - 零售商品：進貨、報損（庫存增減）
 * 新增／編輯／下架品項、成本回填等管理動作仍在網頁版（連結帶 desktop=1）。
 * 全部呼叫既有 service，沒有另外實作業務規則。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileDailyController {

    private final UserService userService;
    private final PerformanceService performanceService;
    private final PerformanceRecordRepository performanceRecordRepository;
    private final StoreSupplyService storeSupplyService;
    private final RetailProductService retailProductService;
    private final OperationLogService operationLogService;
    private final MobileViewHelper view;

    /** 零售商品低庫存門檻，跟 Dashboard 庫存管理區塊一致 */
    private static final int RETAIL_LOW_STOCK = 5;

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

    // ── 我的績效 ────────────────────────────────────────────────────────
    @GetMapping("/perf")
    public String myPerformance(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        var progress = performanceService.getMyProgress(user.getId(), today);

        List<PerformanceRecord> records = performanceRecordRepository
                .findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(user.getId(), monthStart, today);
        // 已經被拆分過的原始紀錄（不能再拆），用來決定要不要顯示「拆給同事」按鈕
        Set<Long> alreadySplit = performanceRecordRepository.findBySplitFromRecordIdIsNotNull().stream()
                .map(PerformanceRecord::getSplitFromRecordId)
                .collect(Collectors.toSet());

        Map<String, List<Map<String, Object>>> byDate = new LinkedHashMap<>();
        Map<String, Double> dayTotal = new LinkedHashMap<>();
        for (PerformanceRecord r : records) {
            String key = r.getServiceDate().isEqual(today) ? "今天" : dateLabel(r.getServiceDate());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("category", r.getCategory() != null ? r.getCategory().getLabel() : "");
            m.put("points", r.getPoints() == null ? 0.0 : r.getPoints());
            m.put("note", r.getNote());
            m.put("source", r.getAppointmentId() != null ? String.format("AP%03d", r.getAppointmentId())
                    : r.getWalkInOrderId() != null ? "現場單#" + r.getWalkInOrderId() : null);
            m.put("sourceUrl", r.getAppointmentId() != null ? "/m/appointments/" + r.getAppointmentId()
                    : r.getWalkInOrderId() != null ? "/m/walk-in/" + r.getWalkInOrderId() : null);
            m.put("fromSplit", r.getSplitFromRecordId() != null);
            m.put("canSplit", r.getSplitFromRecordId() == null && !alreadySplit.contains(r.getId())
                    && r.getPoints() != null && r.getPoints() > 0);
            m.put("split", alreadySplit.contains(r.getId()));
            m.put("label", (r.getCategory() != null ? r.getCategory().getLabel() : "")
                    + "，" + String.format("%.1f", r.getPoints() == null ? 0.0 : r.getPoints()) + " 分");
            byDate.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
            dayTotal.merge(key, r.getPoints() == null ? 0.0 : r.getPoints(), Double::sum);
        }

        // 進度條：本月主要積分 ÷ 下一個級距門檻
        Integer next = progress.getNextThreshold();
        int pct = next == null || next <= 0 ? 100
                : (int) Math.min(100, Math.round(progress.getMonthMainPoints() / next * 100));

        model.addAttribute("user", user);
        model.addAttribute("p", progress);
        model.addAttribute("pct", pct);
        model.addAttribute("monthLabel", today.getMonthValue() + " 月");
        model.addAttribute("byDate", byDate);
        model.addAttribute("dayTotal", dayTotal);
        model.addAttribute("colleagues", view.staffOptions().stream()
                .filter(s -> !user.getId().equals(s.get("id"))).toList());
        model.addAttribute("activeTab", "more");
        return "m/perf";
    }

    @PostMapping("/perf/split")
    public String split(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam Long sourceRecordId,
            @RequestParam(required = false) Long toStaffId,
            @RequestParam(required = false) String note) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (toStaffId == null) {
            ra.addFlashAttribute("toastError", "請選擇要拆給哪位同事");
            return "redirect:/m/perf";
        }
        try {
            // 手機版只能拆自己的紀錄；拆別人的積分請到網頁版績效管理
            PerformanceRecord source = performanceRecordRepository.findById(sourceRecordId).orElse(null);
            if (source == null || source.getStaff() == null || !source.getStaff().getId().equals(user.getId())) {
                ra.addFlashAttribute("toastError", "只能拆分自己的積分");
                return "redirect:/m/perf";
            }
            String n = note == null || note.isBlank() ? null : note.trim();
            performanceService.splitRecord(sourceRecordId, toStaffId, n);
            operationLogService.log(user, "PERFORMANCE", "SPLIT_POINTS", "績效紀錄 #" + sourceRecordId,
                    "拆分給員工 #" + toStaffId + (n != null ? "：" + n : "") + "（手機版）");
            ra.addFlashAttribute("toast", "已把這筆積分對半拆給同事");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "拆分失敗：" + e.getMessage());
        }
        return "redirect:/m/perf";
    }

    // ── 店用洗劑 ────────────────────────────────────────────────────────
    @GetMapping("/supplies")
    public String supplies(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        List<StoreSupply> list = new ArrayList<>(storeSupplyService.listActive());
        // 低於安全庫存的排前面
        list.sort((a, b) -> Boolean.compare(isLow(b), isLow(a)));
        List<Map<String, Object>> rows = list.stream().<Map<String, Object>>map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("stock", s.getStockQuantity());
            m.put("safety", s.getSafetyStockThreshold());
            m.put("low", isLow(s));
            m.put("out", s.getStockQuantity() <= 0);
            return m;
        }).toList();

        model.addAttribute("user", user);
        model.addAttribute("rows", rows);
        model.addAttribute("lowCount", list.stream().filter(this::isLow).count());
        model.addAttribute("activeTab", "more");
        return "m/supplies";
    }

    @PostMapping("/supplies/{id}/use")
    public String useSupply(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra,
            @RequestParam int quantity, @RequestParam(required = false) String note) {
        return supplyAction(request, ra, "USE_SUPPLY", "店用洗劑 #" + id + " 領用 -" + quantity, note,
                "已登記領用 " + quantity,
                u -> storeSupplyService.recordUsage(id, quantity, u.getUsername(), blankToNull(note)));
    }

    @PostMapping("/supplies/{id}/restock")
    public String restockSupply(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra,
            @RequestParam int quantity) {
        return supplyAction(request, ra, "RESTOCK_SUPPLY", "店用洗劑 #" + id + " 進貨 +" + quantity, null,
                "已登記進貨 " + quantity,
                u -> storeSupplyService.restock(id, quantity));
    }

    // ── 零售商品庫存 ────────────────────────────────────────────────────
    @GetMapping("/retail")
    public String retail(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        List<RetailProduct> list = new ArrayList<>(retailProductService.listActive());
        list.sort((a, b) -> Integer.compare(a.getStockQuantity(), b.getStockQuantity())); // 庫存少的排前面
        List<Map<String, Object>> rows = list.stream().<Map<String, Object>>map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("price", p.getPrice());
            m.put("stock", p.getStockQuantity());
            m.put("low", p.getStockQuantity() > 0 && p.getStockQuantity() <= RETAIL_LOW_STOCK);
            m.put("out", p.getStockQuantity() <= 0);
            return m;
        }).toList();

        model.addAttribute("user", user);
        model.addAttribute("rows", rows);
        model.addAttribute("lowCount", list.stream().filter(p -> p.getStockQuantity() <= RETAIL_LOW_STOCK).count());
        model.addAttribute("activeTab", "more");
        return "m/retail";
    }

    // delta 正數＝進貨，負數＝報損／盤點扣減
    @PostMapping("/retail/{id}/adjust")
    public String adjustRetail(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra,
            @RequestParam int delta) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (delta == 0) {
            ra.addFlashAttribute("toastError", "數量不能是 0");
            return "redirect:/m/retail";
        }
        try {
            retailProductService.adjustStock(id, delta);
            operationLogService.log(user, "RETAIL", "ADJUST_RETAIL_STOCK",
                    "商品 #" + id + " 庫存調整 " + (delta > 0 ? "+" : "") + delta, "手機版");
            ra.addFlashAttribute("toast", delta > 0 ? "已進貨 " + delta : "已扣減 " + (-delta));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "調整失敗：" + e.getMessage());
        }
        return "redirect:/m/retail";
    }

    // ────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface StaffAction {
        void run(User user);
    }

    private String supplyAction(HttpServletRequest request, RedirectAttributes ra, String logAction,
            String logTarget, String note, String okMsg, StaffAction action) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            action.run(user);
            operationLogService.log(user, "SUPPLY", logAction, logTarget,
                    (blankToNull(note) != null ? note.trim() + "，" : "") + "手機版");
            ra.addFlashAttribute("toast", okMsg);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
        }
        return "redirect:/m/supplies";
    }

    private boolean isLow(StoreSupply s) {
        return s.getStockQuantity() <= s.getSafetyStockThreshold();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String dateLabel(LocalDate d) {
        String[] w = { "一", "二", "三", "四", "五", "六", "日" };
        DayOfWeek dow = d.getDayOfWeek();
        return d.getMonthValue() + "/" + d.getDayOfMonth() + " 週" + w[dow.getValue() - 1];
    }
}
