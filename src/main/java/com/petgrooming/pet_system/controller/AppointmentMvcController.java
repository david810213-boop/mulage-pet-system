package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentMvcController {

    private final AppointmentService appointmentService;
    private final GroomingService groomingItemService;
    private final UserService userService;
    private final PetService petService;
    private final OperationLogService operationLogService;

    /**
     * JWT 版獲取當前登入使用者
     * 從 LoginInterceptor 存入的 request attribute 拿取 username，
     * 再用 UserService 查出完整的 User entity
     */
    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── GET /appointments ──────────────────────────────────────────────────
    // 💡 作用：查看預約清單。不貼貼紙，因為一般會員與員工登入後都能看（各自看不同範圍）
    // 支援篩選：日期區間、狀態（已確認/已取消）、付款狀態、關鍵字（預約編號/飼主姓名/寵物名稱）
    @GetMapping
    public String list(HttpServletRequest request, Model model,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String paidStatus,
                       @RequestParam(required = false) String keyword) {
        User user = getLoginUser(request);

        model.addAttribute("user", user);

        if (user.isStaffOrAdmin()) {
            // 需求 3 / 7：店家後台看完整資訊（含內部備注、確認狀態）
            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> allAdmin =
                    appointmentService.getAllForAdmin();

            // 頂部統計卡片：一律以「今天」為基準計算，不受篩選條件影響
            java.time.LocalDate today = java.time.LocalDate.now();
            long todayCount = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getDate().isEqual(today))
                    .count();
            long completedCount = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getDate().isEqual(today)
                            && a.getStatus().name().equals("COMPLETED"))
                    .count();
            int todayRevenue = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getDate().isEqual(today) && a.isPaid())
                    .mapToInt(com.petgrooming.pet_system.dto.AppointmentAdminResponse::getTotalAmount)
                    .sum();
            long pendingCount = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getStatus().name().equals("PENDING_CONFIRM"))
                    .count();
            model.addAttribute("todayCount", todayCount);
            model.addAttribute("completedCount", completedCount);
            model.addAttribute("todayRevenue", todayRevenue);
            model.addAttribute("pendingCount", pendingCount);
            model.addAttribute("today", today);

            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> filteredAdmin = allAdmin.stream()
                    .filter(a -> dateFrom == null || !a.getDate().isBefore(dateFrom))
                    .filter(a -> dateTo == null || !a.getDate().isAfter(dateTo))
                    .filter(a -> status == null || status.isBlank() || a.getStatus().name().equals(status))
                    .filter(a -> paidStatus == null || paidStatus.isBlank()
                            || (paidStatus.equals("PAID") == a.isPaid()))
                    .filter(a -> keyword == null || keyword.isBlank() || matchesKeywordAdmin(a, keyword))
                    .toList();
            model.addAttribute("appointments", filteredAdmin);
        } else {
            List<com.petgrooming.pet_system.dto.AppointmentResponse> all =
                    appointmentService.getMyAppointments(user.getUsername());
            List<com.petgrooming.pet_system.dto.AppointmentResponse> filtered = all.stream()
                    .filter(a -> dateFrom == null || !a.getDate().isBefore(dateFrom))
                    .filter(a -> dateTo == null || !a.getDate().isAfter(dateTo))
                    .filter(a -> status == null || status.isBlank() || a.getStatus().name().equals(status))
                    .filter(a -> paidStatus == null || paidStatus.isBlank()
                            || (paidStatus.equals("PAID") == a.isPaid()))
                    .filter(a -> keyword == null || keyword.isBlank() || matchesKeyword(a, keyword))
                    .toList();
            model.addAttribute("appointments", filtered);
        }
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("status", status);
        model.addAttribute("paidStatus", paidStatus);
        model.addAttribute("keyword", keyword);
        return "appointments/list";
    }

    // 關鍵字比對：預約編號（AP001 或純數字）、飼主姓名、寵物名稱
    private boolean matchesKeyword(com.petgrooming.pet_system.dto.AppointmentResponse a, String keyword) {
        String kw = keyword.trim().toLowerCase();
        return a.getAppointmentCode().toLowerCase().contains(kw)
                || String.valueOf(a.getId()).equals(kw)
                || a.getOwnerName().toLowerCase().contains(kw)
                || a.getPetName().toLowerCase().contains(kw);
    }

    private boolean matchesKeywordAdmin(com.petgrooming.pet_system.dto.AppointmentAdminResponse a, String keyword) {
        String kw = keyword.trim().toLowerCase();
        return a.getAppointmentCode().toLowerCase().contains(kw)
                || String.valueOf(a.getId()).equals(kw)
                || a.getOwnerName().toLowerCase().contains(kw)
                || a.getPetName().toLowerCase().contains(kw);
    }

    // ── POST /appointments/{id}/confirm ────────────────────────────────────
    // 需求 3：店家確認預約並敲定最後時間
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(required = false)
                          @org.springframework.format.annotation.DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                          java.time.LocalDateTime confirmedTime,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.confirm(id, confirmedTime, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CONFIRM", "預約 #" + id, null);
            redirectAttributes.addFlashAttribute("successMsg", "已確認預約");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "確認失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── POST /appointments/{id}/notes ──────────────────────────────────────
    // 需求 7：店家設定備注（雙可見性：internalNote 店家專用 / memberNote 會員可見）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/notes")
    public String setNotes(@PathVariable Long id,
                           @RequestParam(required = false) String internalNote,
                           @RequestParam(required = false) String memberNote,
                           HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.setNotes(id, internalNote, memberNote, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "備注已更新");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── GET /appointments/{id}/checkin-order ────────────────────────────────
    // 現場開單（依預約編號）：家長到店後，店員依現場情況確認/調整服務項目
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/checkin-order")
    public String checkinOrderForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        model.addAttribute("user", user);

        var target = appointmentService.getAllForAdmin().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return "redirect:/appointments?status=CONFIRMED";
        }
        model.addAttribute("appointment", target);
        model.addAttribute("groomingItems", groomingItemService.getAllItems());
        return "appointments/checkin-order";
    }

    // ── POST /appointments/{id}/checkin-order ───────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/checkin-order")
    public String checkinOrderSubmit(@PathVariable Long id,
                                     @RequestParam(required = false) List<String> itemCodes,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.confirmCheckinOrder(id, itemCodes, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CHECKIN_ORDER", "預約 #" + id,
                    itemCodes != null ? String.join("、", itemCodes) : null);
            redirectAttributes.addFlashAttribute("successMsg", "已依現場情況開立服務項目，現在可以開始服務");
            return "redirect:/appointments?status=CONFIRMED";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "開單失敗：" + e.getMessage());
            return "redirect:/appointments/" + id + "/checkin-order";
        }
    }

    // ── POST /appointments/checkin-items/{itemId}/operator ──────────────────
    // 補填「現場開單（依預約編號）」項目的經手人（同步寫入績效）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/checkin-items/{itemId}/operator")
    public String fillCheckinItemOperator(@PathVariable Long itemId,
                                          @RequestParam Long staffId,
                                          RedirectAttributes redirectAttributes) {
        try {
            appointmentService.fillItemOperator(itemId, staffId);
            redirectAttributes.addFlashAttribute("successMsg", "已補填經手人");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "補填失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /appointments/{id}/start ───────────────────────────────────────
    // 店員開始服務：已確認 → 進行中（寵物到店開始施作）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.startProgress(id, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "START", "預約 #" + id, null);
            redirectAttributes.addFlashAttribute("successMsg", "已開始服務，狀態轉為進行中");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "操作失敗：" + e.getMessage());
        }
        return "redirect:/appointments?status=IN_PROGRESS";
    }

    // ── GET /appointments/{id}/final-check ──────────────────────────────────
    // 進行中核對頁面：店員與家長核對本次美容狀況，填備注 + 現場簽名
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/final-check")
    public String finalCheckForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        model.addAttribute("user", user);

        var target = appointmentService.getAllForAdmin().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return "redirect:/appointments?status=IN_PROGRESS";
        }
        model.addAttribute("appointment", target);
        model.addAttribute("checkinItems", appointmentService.getCheckinItems(id));
        return "appointments/final-check";
    }

    // ── POST /appointments/{id}/final-check ─────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
                                   @Valid @ModelAttribute com.petgrooming.pet_system.dto.FinalCheckRequest req,
                                   BindingResult bindingResult,
                                   HttpServletRequest request, Model model,
                                   RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);

        if (bindingResult.hasErrors()) {
            var target = appointmentService.getAllForAdmin().stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst().orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("appointment", target);
            model.addAttribute("errorMsg", "請完整填寫備注並完成簽名");
            return "appointments/final-check";
        }

        try {
            appointmentService.finalCheck(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "FINAL_CHECK", "預約 #" + id, req.getNote());
            redirectAttributes.addFlashAttribute("successMsg", "核對完成，已可進行結帳");
            return "redirect:/appointments?status=IN_PROGRESS";
        } catch (IllegalArgumentException e) {
            var target = appointmentService.getAllForAdmin().stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst().orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("appointment", target);
            model.addAttribute("errorMsg", e.getMessage());
            return "appointments/final-check";
        }
    }

    // ── POST /appointments/{id}/cancel ─────────────────────────────────────
    // 💡 作用：取消預約（後台版本，表單提交）
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            var req = new com.petgrooming.pet_system.dto.CancelAppointmentRequest();
            req.setReason(reason);
            appointmentService.cancel(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CANCEL", "預約 #" + id, reason);
            redirectAttributes.addFlashAttribute("successMsg", "預約已取消");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "取消失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── GET /appointments/new ──────────────────────────────────────────────
    // 💡 作用：開啟預約表單。同樣只需登入，不限制特定角色。
    @GetMapping("/new")
    public String newForm(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);

        // 改用 PetResponse，前端才能讀到 sizeCategory、recommendedItemCodes
        List<PetResponse> myPets = petService.getMyPets(user.getUsername());
        List<GroomingItemResponse> availableServices = groomingItemService.getAllItems();

        model.addAttribute("user", user);
        model.addAttribute("myPets", myPets);
        model.addAttribute("appointmentRequest", new AppointmentRequest());
        model.addAttribute("groomingItems", availableServices);
        return "appointments/form";
    }

    // ── GET /appointments/slots ────────────────────────────────────────────
    // 💡 作用：查詢某日期的空檔。
    @GetMapping("/slots")
    public String slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        HttpServletRequest request, Model model) {
        User user = getLoginUser(request);

        model.addAttribute("user", user);
        model.addAttribute("slots", appointmentService.getAvailableSlots(date));
        model.addAttribute("selectedDate", date);
        return "appointments/slots";
    }

    // ── POST /appointments/submit ──────────────────────────────────────────
    // 💡 作用：送出預約表單。
    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute AppointmentRequest req,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {

        User user = getLoginUser(request);

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(user.getUsername()));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            String firstError = bindingResult.getAllErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("表單資料有誤，請確認後再送出");
            model.addAttribute("errorMsg", firstError);
            return "appointments/form";
        }

        try {
            appointmentService.book(req, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "預約成功！");
            return "redirect:/appointments";

        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(user.getUsername()));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "appointments/form";
        }
    }

    // ── 💡 額外加碼練習：管理員專用後台 ─────────────────────────────────────────
    // 🛡️ 啪！貼上你的自訂防偽貼紙。一般會員（CUSTOMER）如果敢打這個網址，直接在 RoleInterceptor 就會被彈飛！
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        // 這裡不需要再撈 user 來檢查了，因為警衛（RoleInterceptor）已經幫你嚴格把關！
        return "appointments/admin_dashboard";
    }
}
