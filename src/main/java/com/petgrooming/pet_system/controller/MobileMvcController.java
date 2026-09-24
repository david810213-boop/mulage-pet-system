package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.MobileAppointmentCard;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.interceptor.MobileRedirectInterceptor;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PendingOperatorMatrixService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import com.petgrooming.pet_system.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 員工手機版（需求，2026-09-24）。
 * 網頁版後台完全保留不動，手機版是另一組獨立頁面，路徑統一在 /m 底下，
 * 後端 service 跟網頁版共用。
 *
 * 第一批：今日、更多、切換網頁版/手機版
 * 第二批：預約列表、預約詳情（確認／開始服務／結束服務／取消／確認匯款／退款）、到店開單
 * 第三批：核對（改項目、自訂金額加購、零售加購、備注、家長簽名）、結帳
 * 第四批：核對、結帳樣板改成預約／現場單共用（現場單另見 MobileWalkInController）
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
    private final GroomingService groomingItemService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final MobileViewHelper view;
    private final WalkInOrderService walkInOrderService;
    private final PendingOperatorMatrixService pendingOperatorMatrixService;

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

        // 第四批：進行中（未結帳）的現場單，今日頁一併列出，方便接著做下一步
        List<Map<String, Object>> openWalkIns = walkInOrderService.listAll().stream()
                .filter(o -> !o.isPaid())
                .limit(8)
                .map(o -> {
                    String title = o.getPetName() != null && !o.getPetName().isBlank() ? o.getPetName()
                            : o.getMemberName() != null ? o.getMemberName() : "非會員";
                    String stage = o.isPendingWireTransfer() ? "待對帳"
                            : !o.isRequiresServiceFlow() || o.isFinalCheckDone() ? "待結帳"
                            : !o.isServiceEndedDone() ? "服務中" : "待核對";
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("title", title);
                    m.put("initial", view.initial(title, "客"));
                    m.put("sub", (o.getMemberName() != null ? o.getMemberName() : "非會員") + "，現場單 #" + o.getId());
                    m.put("stageLabel", stage);
                    return m;
                })
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("openWalkIns", openWalkIns);
        // 第六批：待補經手人的項目數，今日頁提醒
        model.addAttribute("pendingOperatorCount", pendingOperatorMatrixService.buildMobileGroups().stream()
                .mapToInt(g -> g.getLines().size()).sum());
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

    // ── 預約列表 ────────────────────────────────────────────────────────
    // f：upcoming 近期（預設）/ pending 待確認 / serving 服務中 / past 過去 / cancelled 已取消
    // date：只看某一天（優先於 f）
    // q：關鍵字（預約編號、飼主、毛孩名字，優先於 date 跟 f，搜尋全部預約）
    @GetMapping("/appointments")
    public String appointments(HttpServletRequest request, Model model,
            @RequestParam(defaultValue = "upcoming") String f,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String q) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }

        LocalDate today = LocalDate.now();
        List<AppointmentAdminResponse> all = appointmentService.getAllForAdmin();
        Comparator<AppointmentAdminResponse> asc = Comparator.comparing(AppointmentAdminResponse::getDate)
                .thenComparing(AppointmentAdminResponse::getStartTime);

        String keyword = q == null ? "" : q.trim().toLowerCase();
        List<AppointmentAdminResponse> picked;
        if (!keyword.isEmpty()) {
            picked = all.stream()
                    .filter(a -> matchesKeyword(a, keyword))
                    .sorted(asc.reversed())
                    .limit(100)
                    .toList();
        } else if (date != null) {
            picked = all.stream()
                    .filter(a -> a.getDate().isEqual(date))
                    .sorted(asc)
                    .toList();
        } else {
            picked = switch (f) {
                case "pending" -> all.stream()
                        .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                        .filter(a -> !a.getDate().isBefore(today))
                        .sorted(asc).toList();
                case "serving" -> all.stream()
                        .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.IN_PROGRESS)
                        .sorted(asc).toList();
                case "past" -> all.stream()
                        .filter(a -> !a.isCancelled() && a.getDate().isBefore(today)
                                && !a.getDate().isBefore(today.minusDays(60)))
                        .sorted(asc.reversed()).limit(150).toList();
                case "cancelled" -> all.stream()
                        .filter(AppointmentAdminResponse::isCancelled)
                        .sorted(asc.reversed()).limit(50).toList();
                default -> all.stream()
                        .filter(a -> !a.isCancelled() && !a.getDate().isBefore(today))
                        .sorted(asc).limit(150).toList();
            };
        }

        // 依日期分組，保留排序
        Map<String, List<MobileAppointmentCard>> groups = new LinkedHashMap<>();
        for (AppointmentAdminResponse a : picked) {
            String key = a.getDate().isEqual(today) ? "今天 " + dateLabel(a.getDate())
                    : a.getDate().isEqual(today.plusDays(1)) ? "明天 " + dateLabel(a.getDate())
                    : dateLabel(a.getDate());
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(toCard(a, today));
        }

        // 日期列：今天起 7 天，每天的預約數
        List<Map<String, Object>> days = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            long count = all.stream().filter(a -> !a.isCancelled() && a.getDate().isEqual(d)).count();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("iso", d.toString());
            day.put("week", i == 0 ? "今天" : weekdayLabel(d.getDayOfWeek()).substring(1));
            day.put("day", d.getDayOfMonth());
            day.put("count", count);
            day.put("on", d.equals(date));
            days.add(day);
        }

        model.addAttribute("user", user);
        model.addAttribute("groups", groups);
        model.addAttribute("total", picked.size());
        model.addAttribute("days", days);
        model.addAttribute("f", date != null || !keyword.isEmpty() ? "" : f);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("selectedDate", date);
        model.addAttribute("pendingCount", all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .filter(a -> !a.getDate().isBefore(today)).count());
        model.addAttribute("servingCount", all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.IN_PROGRESS).count());
        model.addAttribute("activeTab", "appointments");
        return "m/appointments";
    }

    // ── 預約詳情 ────────────────────────────────────────────────────────
    @GetMapping("/appointments/{id}")
    public String appointmentDetail(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null) {
            return "redirect:/m/appointments";
        }
        PetResponse pet = null;
        try {
            pet = appointmentService.getPetForAppointment(id);
        } catch (IllegalArgumentException e) {
            // 找不到寵物檔案（例如寵物已刪除）不影響顯示預約本身
        }
        MobileAppointmentCard card = toCard(a, LocalDate.now());

        model.addAttribute("user", user);
        model.addAttribute("a", a);
        model.addAttribute("card", card);
        model.addAttribute("pet", pet);
        model.addAttribute("step", stepIndex(card.getStage()));
        model.addAttribute("canCheckout", !a.isCancelled() && !a.isPaid() && !a.isPendingWireTransfer()
                && (a.getStatus() != AppointmentStatus.IN_PROGRESS || a.isFinalCheckDone()));
        model.addAttribute("canCancel", !a.isCancelled() && !a.isPaid());
        model.addAttribute("activeTab", "appointments");
        return "m/appointment-detail";
    }

    // ── 確認預約 ────────────────────────────────────────────────────────
    @PostMapping("/appointments/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam(required = false) String back,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return runAction(id, back, request, redirectAttributes, "CONFIRM", "已確認預約",
                user -> appointmentService.confirm(id, null, user.getUsername()));
    }

    // ── 開始服務：已確認且已開單 → 進行中 ─────────────────────────────────
    @PostMapping("/appointments/{id}/start")
    public String start(@PathVariable Long id, @RequestParam(required = false) String back,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return runAction(id, back, request, redirectAttributes, "START", "已開始服務",
                user -> appointmentService.startProgress(id, user.getUsername()));
    }

    // ── 結束服務：通知家長來店接寵物 ─────────────────────────────────────
    @PostMapping("/appointments/{id}/end-service")
    public String endService(@PathVariable Long id, @RequestParam(required = false) String back,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return runAction(id, back, request, redirectAttributes, "END_SERVICE", "已結束服務，已通知家長來接",
                user -> appointmentService.endService(id, user.getUsername()));
    }

    // ── 取消預約 ────────────────────────────────────────────────────────
    @PostMapping("/appointments/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String finalReason = (reason == null || reason.isBlank()) ? "店家手機版取消" : reason.trim();
        return runAction(id, null, request, redirectAttributes, "CANCEL", "預約已取消",
                user -> {
                    CancelAppointmentRequest req = new CancelAppointmentRequest();
                    req.setReason(finalReason);
                    appointmentService.cancel(id, req, user.getUsername());
                });
    }

    // ── 確認匯款收款（待對帳 → 已完成）────────────────────────────────────
    @PostMapping("/appointments/{id}/confirm-wire")
    public String confirmWire(@PathVariable Long id, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        return runAction(id, null, request, redirectAttributes, "CONFIRM_WIRE_TRANSFER", "已確認收款",
                user -> paymentService.confirmWireTransferPayment(id, user.getUsername()));
    }

    // ── 退款：回到已確認狀態，清空開單／服務／核對進度 ──────────────────
    @PostMapping("/appointments/{id}/refund")
    public String refund(@PathVariable Long id, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        return runAction(id, null, request, redirectAttributes, "REFUND", "已退款，預約回到已確認狀態",
                user -> paymentService.refund(id, user.getUsername()));
    }

    // ── 到店開單：依現場情況勾選本次實際服務項目 ─────────────────────────
    @GetMapping("/appointments/{id}/checkin")
    public String checkinForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null || a.isCancelled()) {
            return "redirect:/m/appointments";
        }
        boolean isExisting = appointmentService.isExistingCustomerPet(id);
        PetResponse pet = appointmentService.getPetForAppointment(id);
        List<GroomingItemResponse> items = groomingMenuFilter.filterForPetShape(
                groomingItemService.getAllItems(), isExisting, a.getPetType(), pet);

        // 顧客預約時選的項目，開單頁預設幫忙勾好，店員只要改有變動的部分
        Set<String> preselected = a.getSelectedItems() == null ? Set.of()
                : a.getSelectedItems().stream().map(GroomingItem::getItemCode)
                        .collect(Collectors.toSet());

        model.addAttribute("user", user);
        model.addAttribute("a", a);
        model.addAttribute("card", toCard(a, LocalDate.now()));
        model.addAttribute("pet", pet);
        model.addAttribute("mainItems", items.stream()
                .filter(i -> !"OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("otherItems", items.stream()
                .filter(i -> "OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("preselected", preselected);
        model.addAttribute("activeTab", "appointments");
        return "m/checkin";
    }

    @PostMapping("/appointments/{id}/checkin")
    public String checkinSubmit(@PathVariable Long id,
            @RequestParam(required = false) List<String> itemCodes,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (itemCodes == null || itemCodes.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "請至少勾選一個服務項目");
            return "redirect:/m/appointments/" + id + "/checkin";
        }
        try {
            appointmentService.confirmCheckinOrder(id, itemCodes, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CHECKIN_ORDER", "預約 #" + id,
                    String.join("、", itemCodes) + "（手機版）");
            redirectAttributes.addFlashAttribute("toast", "已開單，可以開始服務了");
            return "redirect:/m/appointments/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", "開單失敗：" + e.getMessage());
            return "redirect:/m/appointments/" + id + "/checkin";
        }
    }

    // ── 核對：跟家長確認本次項目與金額、填美容狀況備注、家長簽名 ─────────
    @GetMapping("/appointments/{id}/final-check")
    public String finalCheckForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null || a.isCancelled()) {
            return "redirect:/m/appointments";
        }
        if (a.isFinalCheckDone() || a.getStatus() != AppointmentStatus.IN_PROGRESS || !a.isServiceEndedDone()) {
            return "redirect:/m/appointments/" + id;
        }
        List<com.petgrooming.pet_system.model.AppointmentItem> items = appointmentService.getCheckinItems(id);
        // 跟網頁版核對頁同一套：只篩「僅限既有客戶」＋「適用物種」，不篩體型，
        // 讓店員現場可以補任何尺寸的項目
        boolean isExisting = appointmentService.isExistingCustomerPet(id);
        List<GroomingItemResponse> addable = groomingMenuFilter.filterFor(
                groomingItemService.getAllItems(), isExisting, a.getPetType());
        MobileAppointmentCard card = toCard(a, LocalDate.now());

        model.addAttribute("user", user);
        // 核對頁樣板預約、現場單共用，網址前綴跟頁首資訊由 controller 決定
        model.addAttribute("base", "/m/appointments/" + id);
        model.addAttribute("backUrl", "/m/appointments/" + id);
        model.addAttribute("subtitle", card.getPetName() + "，" + a.getOwnerName());
        model.addAttribute("avSpecies", card.getSpecies());
        model.addAttribute("avInitial", card.getPetInitial());
        model.addAttribute("lines", view.fromAppointmentItems(items));
        model.addAttribute("linesTotal", items.stream().mapToInt(com.petgrooming.pet_system.model.AppointmentItem::getPrice).sum());
        model.addAttribute("groomingItems", addable);
        model.addAttribute("retailProducts", retailProductService.listActive());
        model.addAttribute("performanceCategories", PerformanceCategory.values());
        model.addAttribute("activeTab", "appointments");
        return "m/final-check";
    }

    @PostMapping("/appointments/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String signatureData,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            FinalCheckRequest req = new FinalCheckRequest();
            req.setNote(note);
            req.setSignatureData(signatureData);
            appointmentService.finalCheck(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "FINAL_CHECK", "預約 #" + id,
                    (note == null ? "" : note) + "（手機版）");
            redirectAttributes.addFlashAttribute("toast", "核對完成，可以結帳了");
            return "redirect:/m/appointments/" + id + "/checkout";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", e.getMessage());
            redirectAttributes.addFlashAttribute("draftNote", note);
            return "redirect:/m/appointments/" + id + "/final-check";
        }
    }

    // ── 核對／結帳頁共用：改項目 ───────────────────────────────────────
    // from=check 回核對頁，其他回結帳頁（跟網頁版 redirectAfterEdit 同樣的做法）
    @PostMapping("/appointments/{id}/items/grooming")
    public String addGroomingItem(@PathVariable Long id, @RequestParam Long groomingItemId,
            @RequestParam(required = false) Integer customPrice,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入項目",
                user -> appointmentService.addGroomingItem(id, groomingItemId, customPrice, user.getUsername()));
    }

    @PostMapping("/appointments/{id}/items/custom")
    public String addCustomItem(@PathVariable Long id, @RequestParam String itemName,
            @RequestParam int price,
            @RequestParam(required = false) PerformanceCategory category,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入「" + itemName + "」",
                user -> appointmentService.addCustomItem(id, itemName, price, category, user.getUsername()));
    }

    @PostMapping("/appointments/{id}/items/retail")
    public String addRetailItem(@PathVariable Long id, @RequestParam Long retailProductId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入商品",
                user -> appointmentService.addRetailItem(id, retailProductId, quantity, user.getUsername()));
    }

    @PostMapping("/appointments/{id}/items/{itemId}/remove")
    public String removeItem(@PathVariable Long id, @PathVariable Long itemId,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已移除項目",
                user -> appointmentService.removeItem(id, itemId, user.getUsername()));
    }

    // ── 結帳 ────────────────────────────────────────────────────────────
    @GetMapping("/appointments/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null || a.isCancelled()) {
            return "redirect:/m/appointments";
        }
        boolean canCheckout = !a.isPaid() && !a.isPendingWireTransfer()
                && (a.getStatus() != AppointmentStatus.IN_PROGRESS || a.isFinalCheckDone());
        if (!canCheckout) {
            return "redirect:/m/appointments/" + id;
        }

        var detail = appointmentService.getAppointmentDetail(id, user.getUsername());
        var wallet = walletService.getWallet(a.getOwnerEmail());
        int balance = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int walletAmount = paymentService.previewWalletAmount(id);
        PetResponse pet = null;
        try {
            pet = appointmentService.getPetForAppointment(id);
        } catch (IllegalArgumentException e) {
            // 找不到寵物檔案不影響結帳
        }
        // 可以鎖定成固定套餐的項目：狗狗、還沒鎖定、單子裡有帶體重級距的套餐項目
        var lockable = (pet != null && "dog".equals(view.speciesKey(a.getPetType())) && pet.getLockedGroomingItemId() == null)
                ? detail.getItems().stream()
                        .filter(it -> it.getGroomingItemId() != null && it.getDogWeightTier() != null)
                        .findFirst().orElse(null)
                : null;
        MobileAppointmentCard card = toCard(a, LocalDate.now());

        model.addAttribute("user", user);
        // 結帳頁樣板預約、現場單共用
        model.addAttribute("base", "/m/appointments/" + id);
        model.addAttribute("backUrl", "/m/appointments/" + id);
        model.addAttribute("subtitle", card.getPetName() + "，" + a.getOwnerName());
        model.addAttribute("avSpecies", card.getSpecies());
        model.addAttribute("avInitial", card.getPetInitial());
        model.addAttribute("lines", view.fromAppointmentDetail(detail.getItems()));
        model.addAttribute("baseAmount", detail.getTotalAmount());
        model.addAttribute("standardAmount", paymentService.previewStandardAmount(id));
        model.addAttribute("walletAmount", walletAmount);
        model.addAttribute("walletBalance", balance);
        model.addAttribute("walletEnough", balance >= walletAmount);
        model.addAttribute("walletDiscountLabel", wallet.isCardActive() && wallet.getDiscount() < 1.0
                ? wallet.getCardTierLabel() + " " + view.formatDiscount(wallet.getDiscount()) : null);
        model.addAttribute("bank", paymentService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
        model.addAttribute("retailProducts", retailProductService.listActive());
        if (lockable != null) {
            model.addAttribute("lockPetId", pet.getId());
            model.addAttribute("lockItemId", lockable.getGroomingItemId());
            model.addAttribute("lockItemName", lockable.getName());
        }
        model.addAttribute("activeTab", "appointments");
        return "m/checkout";
    }

    @PostMapping("/appointments/{id}/checkout")
    public String checkoutSubmit(@PathVariable Long id,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (paymentMethod == null || paymentMethod == PaymentMethod.CREDIT_CARD) {
            redirectAttributes.addFlashAttribute("toastError", "請選擇付款方式");
            return "redirect:/m/appointments/" + id + "/checkout";
        }
        try {
            CheckoutRequest req = new CheckoutRequest();
            req.setPaymentMethod(paymentMethod);
            var result = paymentService.checkout(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CHECKOUT", "預約 #" + id,
                    paymentMethod.name() + "（手機版）");

            // 跟網頁版一樣：狗狗還沒鎖定固定套餐的話，結帳完提醒更新體重
            try {
                PetResponse pet = appointmentService.getPetForAppointment(id);
                if (pet != null && pet.getPetType() != null && "DOG".equalsIgnoreCase(pet.getPetType().name())
                        && pet.getLockedGroomingItemId() == null) {
                    redirectAttributes.addFlashAttribute("weightReminderPetId", pet.getId());
                    redirectAttributes.addFlashAttribute("weightReminderPetName", pet.getName());
                    redirectAttributes.addFlashAttribute("weightReminderCurrentWeight", pet.getWeight());
                }
            } catch (IllegalArgumentException ignore) {
                // 提醒只是輔助功能，找不到寵物就不提醒
            }

            redirectAttributes.addFlashAttribute("toast", result.isPaid()
                    ? "結帳完成"
                    : "已送出，等收到匯款後記得按「確認已收到匯款」");
            return "redirect:/m/appointments/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", "結帳失敗：" + e.getMessage());
            return "redirect:/m/appointments/" + id + "/checkout";
        }
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

    @FunctionalInterface
    private interface StaffAction {
        void run(User user);
    }

    // 狀態變更類操作共用：執行、寫操作紀錄、帶提示訊息回到指定頁面
    private String runAction(Long id, String back, HttpServletRequest request,
            RedirectAttributes redirectAttributes, String logAction, String okMsg, StaffAction action) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            action.run(user);
            operationLogService.log(user, "APPOINTMENT", logAction, "預約 #" + id, "手機版");
            redirectAttributes.addFlashAttribute("toast", okMsg);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", "操作失敗：" + e.getMessage());
        }
        return "redirect:" + safeBack(back, "/m/appointments/" + id);
    }

    // 改項目類操作共用：執行後依 from 回到核對頁或結帳頁
    private String editItems(Long id, String from, HttpServletRequest request,
            RedirectAttributes redirectAttributes, String okMsg, StaffAction action) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            action.run(user);
            redirectAttributes.addFlashAttribute("toast", okMsg);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", e.getMessage());
        }
        return "check".equals(from)
                ? "redirect:/m/appointments/" + id + "/final-check"
                : "redirect:/m/appointments/" + id + "/checkout";
    }

    // 只接受站內 /m/ 開頭的路徑，避免被帶去外部網站
    private String safeBack(String back, String fallback) {
        if (back != null && back.startsWith("/m/") && !back.contains("//") && !back.contains("\\")) {
            return back;
        }
        return fallback;
    }

    private AppointmentAdminResponse findAppointment(Long id) {
        return appointmentService.getAllForAdmin().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesKeyword(AppointmentAdminResponse a, String kw) {
        return (a.getAppointmentCode() != null && a.getAppointmentCode().toLowerCase().contains(kw))
                || String.valueOf(a.getId()).equals(kw)
                || (a.getOwnerName() != null && a.getOwnerName().toLowerCase().contains(kw))
                || (a.getPetName() != null && a.getPetName().toLowerCase().contains(kw));
    }

    // 流程階段，判斷順序跟網頁版預約列表的按鈕顯示條件一致
    private String stageKey(AppointmentAdminResponse a) {
        if (a.isCancelled()) return "cancelled";
        if (a.isPaid()) return "done";
        if (a.isPendingWireTransfer()) return "wire";
        if (a.getStatus() == AppointmentStatus.PENDING_CONFIRM) return "confirm";
        if (a.getStatus() == AppointmentStatus.CONFIRMED) {
            return a.isCheckinOrderConfirmed() ? "start" : "checkin";
        }
        if (a.getStatus() == AppointmentStatus.IN_PROGRESS) {
            if (!a.isServiceEndedDone()) return "serving";
            if (!a.isFinalCheckDone()) return "check";
        }
        return "pay";
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case "cancelled" -> "已取消";
            case "done" -> "已結帳";
            case "wire" -> "待對帳";
            case "confirm" -> "待確認";
            case "checkin" -> "待開單";
            case "start" -> "待開始";
            case "serving" -> "服務中";
            case "check" -> "待核對";
            default -> "待結帳";
        };
    }

    // 詳情頁流程條：0 確認、1 開單、2 服務、3 核對、4 結帳、5 全部完成
    private int stepIndex(String stage) {
        return switch (stage) {
            case "confirm" -> 0;
            case "checkin" -> 1;
            case "start", "serving" -> 2;
            case "check" -> 3;
            case "pay", "wire" -> 4;
            case "done" -> 5;
            default -> -1;
        };
    }

    private String dateLabel(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + " " + weekdayLabel(d.getDayOfWeek());
    }

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
        String stage = stageKey(a);
        return MobileAppointmentCard.builder()
                .id(a.getId())
                .dateLabel(dateLabel(a.getDate()))
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
                .stage(stage)
                .stageLabel(stageLabel(stage))
                .build();
    }

    private String speciesKey(String petType) {
        return view.speciesKey(petType);
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
