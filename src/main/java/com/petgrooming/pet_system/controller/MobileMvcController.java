package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.MobileAppointmentCard;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.interceptor.MobileRedirectInterceptor;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 員工手機版（需求，2026-09-24）。
 * 網頁版後台完全保留不動，手機版是另一組獨立頁面，路徑統一在 /m 底下，
 * 後端 service 跟網頁版共用。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileMvcController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long VIEW_MODE_MAX_AGE = 60L * 60 * 24 * 365;

    private final AppointmentService appointmentService;
    private final UserService userService;
    private final TopUpService topUpService;
    private final RetailProductService retailProductService;
    private final OperationLogService operationLogService;

    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

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

    // ── 今日 ────────────────────────────────────────────────────────────
    @GetMapping({ "", "/", "/today" })
    public String today(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<AppointmentAdminResponse> all = appointmentService.getAllForAdmin();

        List<MobileAppointmentCard> todayCards = all.stream()
                .filter(a -> !a.isCancelled() && a.getDate().isEqual(today))
                .sorted(Comparator.comparing(AppointmentAdminResponse::getStartTime))
                .map(a -> toCard(a, today))
                .toList();

        // 「現在」紅線要插在第一筆開始時間晚於現在的卡片前面；全部都過了就放最後
        int nowIndex = todayCards.size();
        for (int i = 0; i < todayCards.size(); i++) {
            if (LocalTime.parse(todayCards.get(i).getTime(), TIME_FMT).isAfter(now)) {
                nowIndex = i;
                break;
            }
        }

        // 之後日期的待確認預約（今天的已經在時間軸裡，不重複列）
        List<MobileAppointmentCard> upcomingPending = all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .filter(a -> a.getDate().isAfter(today))
                .sorted(Comparator.comparing(AppointmentAdminResponse::getDate)
                        .thenComparing(AppointmentAdminResponse::getStartTime))
                .limit(10)
                .map(a -> toCard(a, today))
                .toList();

        long pendingConfirmCount = all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .filter(a -> !a.getDate().isBefore(today))
                .count();
        long lowStockCount = retailProductService.listActive().stream()
                .filter(p -> p.getStockQuantity() <= 5)
                .count();

        model.addAttribute("user", user);
        model.addAttribute("greeting", greeting(now));
        model.addAttribute("todayLabel", today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日 "
                + weekdayLabel(today.getDayOfWeek()));
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

    // ── 確認預約 ────────────────────────────────────────────────────────
    @PostMapping("/appointments/{id}/confirm")
    public String confirm(@PathVariable Long id, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            appointmentService.confirm(id, null, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CONFIRM", "預約 #" + id, "手機版");
            redirectAttributes.addFlashAttribute("toast", "已確認預約");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", "確認失敗：" + e.getMessage());
        }
        return "redirect:/m/";
    }

    // ── 更多（全部功能入口）──────────────────────────────────────────────
    @GetMapping("/more")
    public String more(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
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

    // ────────────────────────────────────────────────────────────────────

    private MobileAppointmentCard toCard(AppointmentAdminResponse a, LocalDate today) {
        String species = speciesKey(a.getPetType());
        String items;
        if (a.getDisplayItemNames() != null && !a.getDisplayItemNames().isEmpty()) {
            items = String.join("、", a.getDisplayItemNames());
        } else if (a.getSelectedItems() != null) {
            items = a.getSelectedItems().stream().map(GroomingItem::getName).collect(Collectors.joining("、"));
        } else {
            items = "";
        }
        String petName = a.getPetName() == null ? "" : a.getPetName();
        return MobileAppointmentCard.builder()
                .id(a.getId())
                .dateLabel(a.getDate().getMonthValue() + "/" + a.getDate().getDayOfMonth() + " "
                        + weekdayLabel(a.getDate().getDayOfWeek()))
                .time(a.getStartTime().format(TIME_FMT))
                .petName(petName)
                .petInitial(petName.isEmpty() ? "?" : petName.substring(0, petName.offsetByCodePoints(0, 1)))
                .species(species)
                .speciesLabel("dog".equals(species) ? "狗狗" : "cat".equals(species) ? "貓咪" : "其他")
                .items(items)
                .ownerName(a.getOwnerName())
                .state(stateKey(a))
                .statusLabel(a.getStatusLabel())
                .note(a.getInternalNote())
                .totalAmount(a.getTotalAmount())
                .pending(a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .build();
    }

    private String speciesKey(String petType) {
        if ("DOG".equalsIgnoreCase(petType)) {
            return "dog";
        }
        if ("CAT".equalsIgnoreCase(petType)) {
            return "cat";
        }
        return "other";
    }

    private String stateKey(AppointmentAdminResponse a) {
        if (a.getStatus() == AppointmentStatus.COMPLETED || a.isPaid()) {
            return "done";
        }
        if (a.getStatus() == AppointmentStatus.IN_PROGRESS) {
            return "live";
        }
        if (a.getStatus() == AppointmentStatus.PENDING_CONFIRM) {
            return "pending";
        }
        return "normal";
    }

    private String greeting(LocalTime now) {
        if (now.isBefore(LocalTime.of(11, 0))) {
            return "早安";
        }
        if (now.isBefore(LocalTime.of(17, 0))) {
            return "午安";
        }
        return "晚安";
    }

    private String weekdayLabel(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "週一";
            case TUESDAY -> "週二";
            case WEDNESDAY -> "週三";
            case THURSDAY -> "週四";
            case FRIDAY -> "週五";
            case SATURDAY -> "週六";
            case SUNDAY -> "週日";
        };
    }
}
