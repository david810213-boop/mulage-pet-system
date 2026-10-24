package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.MobileAppointmentCard;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.interceptor.MobileRedirectInterceptor;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentListService;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.MobileAppointmentCards;
import com.petgrooming.pet_system.service.PendingOperatorMatrixService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 員工手機版（需求，2026-09-24）：今日、預約列表、更多、切換網頁版／手機版。
 * 網頁版後台完全保留不動，手機版是另一組獨立頁面，路徑統一在 /m 底下，後端 service 跟網頁版共用。
 *
 * 預約詳情與流程（開單、核對、結帳）見 MobileAppointmentController；
 * 現場單見 MobileWalkInController（列表、開單）與 MobileWalkInOrderController（明細與流程）。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileMvcController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long VIEW_MODE_MAX_AGE = 60L * 60 * 24 * 365;

    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentListService appointmentListService;
    private final TopUpService topUpService;
    private final RetailProductService retailProductService;
    private final MobileAppointmentCards cards; // 預約卡片、流程階段與日期標籤
    private final PendingOperatorMatrixService pendingOperatorMatrixService;

    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;


    // ── 今日 ────────────────────────────────────────────────────────────
    @GetMapping({ "", "/", "/today" })
    public String today(Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<AppointmentAdminResponse> all = appointmentQueryService.getAllForAdmin();

        List<MobileAppointmentCard> todayCards = all.stream()
                .filter(a -> !a.isCancelled() && a.getDate().isEqual(today))
                .sorted(Comparator.comparing(AppointmentAdminResponse::getStartTime))
                .map(a -> cards.toCard(a, today))
                .toList();

        int nowIndex = cards.nowIndex(todayCards, now);

        // 之後日期的待確認預約（今天的已經在時間軸裡，不重複列）
        List<MobileAppointmentCard> upcomingPending = all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .filter(a -> a.getDate().isAfter(today))
                .sorted(Comparator.comparing(AppointmentAdminResponse::getDate)
                        .thenComparing(AppointmentAdminResponse::getStartTime))
                .limit(10)
                .map(a -> cards.toCard(a, today))
                .toList();

        long pendingConfirmCount = all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .filter(a -> !a.getDate().isBefore(today))
                .count();
        long lowStockCount = retailProductService.listActive().stream()
                .filter(p -> p.getStockQuantity() <= 5)
                .count();

        model.addAttribute("user", user);
        model.addAttribute("openWalkIns", cards.openWalkInCards());
        // 第六批：待補經手人的項目數，今日頁提醒
        model.addAttribute("pendingOperatorCount", pendingOperatorMatrixService.buildMobileGroups().stream()
                .mapToInt(g -> g.getLines().size()).sum());
        model.addAttribute("greeting", cards.greeting(now));
        model.addAttribute("todayLabel", today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日 "
                + cards.weekdayLabel(today.getDayOfWeek()));
        model.addAttribute("todayCards", todayCards);
        model.addAttribute("nowIndex", nowIndex);
        model.addAttribute("nowLabel", now.format(TIME_FMT));
        model.addAttribute("upcomingPending", upcomingPending);
        model.addAttribute("pendingConfirmCount", pendingConfirmCount);
        model.addAttribute("pendingTopupCount", topUpService.pending().size());
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("activeTab", "today");
        return "m/today";
    }

    // ── 預約列表 ────────────────────────────────────────────────────────
    // f：upcoming 近期（預設）/ pending 待確認 / serving 服務中 / past 過去 / cancelled 已取消
    // date：只看某一天（優先於 f）
    // q：關鍵字（預約編號、飼主、毛孩名字，優先於 date 跟 f，搜尋全部預約）
    @GetMapping("/appointments")
    public String appointments(Model model,
            @RequestParam(defaultValue = "upcoming") String f,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String q,
            @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }

        LocalDate today = LocalDate.now();
        String keyword = q == null ? "" : q.trim();
        var list = appointmentListService.forMobile(f, date, keyword, today);

        model.addAttribute("user", user);
        model.addAttribute("groups", cards.groupByDate(list.picked(), today));
        model.addAttribute("total", list.picked().size());
        model.addAttribute("days", cards.dayStrip(list.dayCounts(), today, date));
        model.addAttribute("f", date != null || !keyword.isEmpty() ? "" : f);
        model.addAttribute("q", keyword);
        model.addAttribute("selectedDate", date);
        model.addAttribute("pendingCount", list.pendingCount());
        model.addAttribute("servingCount", list.servingCount());
        model.addAttribute("activeTab", "appointments");
        return "m/appointments";
    }

    // ── 更多（全部功能入口）──────────────────────────────────────────────
    @GetMapping("/more")
    public String more(Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("activeTab", "more");
        return "m/more";
    }

    // ── 切換網頁版 / 手機版 ─────────────────────────────────────────────
    @GetMapping("/switch")
    public String switchView(@RequestParam(defaultValue = "desktop") String to,
            HttpServletResponse response) {
        String mode = "mobile".equals(to) ? "mobile" : "desktop";
        response.addHeader("Set-Cookie", CookieUtils.buildJwtCookieHeader(
                MobileRedirectInterceptor.VIEW_MODE_COOKIE, mode, VIEW_MODE_MAX_AGE, cookieSecure));
        return "mobile".equals(mode) ? "redirect:/m/" : "redirect:/dashboard";
    }
}
