package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.SlotCapacityService;
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
    private final SlotCapacityService slotCapacityService;
    private final com.petgrooming.pet_system.service.ClosedDateService closedDateService; // 需求 16：公休日設定
    private final com.petgrooming.pet_system.service.PaymentService paymentService;
    private final com.petgrooming.pet_system.service.RetailProductService retailProductService; // 需求（追加）：核對頁編輯訂單

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
        // 需求（追加）：僅限既有客戶的項目，這隻寵物還沒消費過的話直接從選單濾掉
        boolean isExisting = appointmentService.isExistingCustomerPet(id);
        var filteredItems = groomingItemService.getAllItems().stream()
                .filter(i -> isExisting || !i.isRequiresExistingCustomer())
                .toList();
        model.addAttribute("groomingItems", filteredItems);
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
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.fillItemOperator(itemId, staffId);
            operationLogService.log(user, "APPOINTMENT", "FILL_OPERATOR",
                    "項目 #" + itemId, "指定經手人 #" + staffId);
            redirectAttributes.addFlashAttribute("successMsg", "已補填經手人");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "補填失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /appointments/checkin-items/operator/batch ──────────────────
    // 需求 11：批次補填經手人（依預約編號現場開單這邊的待補清單）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/checkin-items/operator/batch")
    public String fillCheckinItemOperatorBatch(@RequestParam("itemIds") List<Long> itemIds,
                                               @RequestParam("staffIds") List<String> staffIds,
                                               HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        int filled = 0;
        for (int i = 0; i < itemIds.size(); i++) {
            String staffIdStr = i < staffIds.size() ? staffIds.get(i) : null;
            if (staffIdStr == null || staffIdStr.isBlank()) continue;
            try {
                Long staffId = Long.valueOf(staffIdStr);
                appointmentService.fillItemOperator(itemIds.get(i), staffId);
                operationLogService.log(user, "APPOINTMENT", "FILL_OPERATOR",
                        "項目 #" + itemIds.get(i), "指定經手人 #" + staffId);
                filled++;
            } catch (Exception e) {
                ra.addFlashAttribute("errorMsg", "項目 #" + itemIds.get(i) + " 補填失敗：" + e.getMessage());
            }
        }
        if (filled > 0) {
            ra.addFlashAttribute("successMsg", "已一次性補填 " + filled + " 筆經手人");
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

    // ── POST /appointments/{id}/end-service ───────────────────────────────
    // 服務項目全部做完，通知家長來店接寵物；狀態維持進行中，之後才能核對
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/end-service")
    public String endService(@PathVariable Long id, HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.endService(id, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "END_SERVICE", "預約 #" + id, null);
            redirectAttributes.addFlashAttribute("successMsg", "已結束服務，已通知家長來店接寵物");
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
        // 需求（追加）：僅限既有客戶的項目，這隻寵物還沒消費過的話直接從選單濾掉
        boolean isExisting = appointmentService.isExistingCustomerPet(id);
        var filteredItems = groomingItemService.getAllItems().stream()
                .filter(i -> isExisting || !i.isRequiresExistingCustomer())
                .toList();
        model.addAttribute("groomingItems", filteredItems); // 需求（追加）：核對頁也能直接編輯訂單項目
        model.addAttribute("retailProducts", retailProductService.listActive());
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
            model.addAttribute("checkinItems", appointmentService.getCheckinItems(id)); // 需求（追加）修正：漏帶導致誤顯示「尚未開單」
            boolean isExisting1 = appointmentService.isExistingCustomerPet(id); // 需求（追加）
            model.addAttribute("groomingItems", groomingItemService.getAllItems().stream()
                    .filter(i -> isExisting1 || !i.isRequiresExistingCustomer()).toList());
            model.addAttribute("retailProducts", retailProductService.listActive());
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
            model.addAttribute("checkinItems", appointmentService.getCheckinItems(id)); // 需求（追加）修正：同上
            boolean isExisting2 = appointmentService.isExistingCustomerPet(id); // 需求（追加）
            model.addAttribute("groomingItems", groomingItemService.getAllItems().stream()
                    .filter(i -> isExisting2 || !i.isRequiresExistingCustomer()).toList());
            model.addAttribute("retailProducts", retailProductService.listActive());
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
    public String newForm(HttpServletRequest request, Model model,
                          @RequestParam(required = false) String forUsername) {
        User user = getLoginUser(request);

        // 需求 20：店家/員工可代客建立預約——forUsername 有帶值時，
        // 幫「那個會員」查寵物清單，而不是查登入者自己的寵物。
        String targetUsername = user.getUsername();
        com.petgrooming.pet_system.model.User targetUser = user;
        if (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) {
            try {
                targetUser = userService.getUserEntityByUsername(forUsername);
                targetUsername = targetUser.getUsername();
            } catch (IllegalArgumentException e) {
                return "redirect:/appointments/select-member";
            }
        }

        // 改用 PetResponse，前端才能讀到 sizeCategory、recommendedItemCodes
        List<PetResponse> myPets = petService.getMyPets(targetUsername);
        List<GroomingItemResponse> availableServices = groomingItemService.getAllItems();

        model.addAttribute("user", user);
        model.addAttribute("myPets", myPets);
        model.addAttribute("appointmentRequest", new AppointmentRequest());
        model.addAttribute("groomingItems", availableServices);
        model.addAttribute("companySignatureImage", paymentService.getCompanySignatureImage());
        // 代客建立時，畫面要顯示「正在為誰建立」，送出時也要帶著這個值
        model.addAttribute("forUsername", (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) ? targetUsername : null);
        model.addAttribute("targetMemberName", (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) ? targetUser.getName() : null);
        return "appointments/form";
    }

    // ── GET /appointments/select-member ──────────────────────────────────
    // 需求 20：店家代客建立預約——先搜尋/選定要幫哪位會員預約
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/select-member")
    public String selectMemberForm(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        return "appointments/select-member";
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
                         @RequestParam(required = false) String forUsername,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {

        User user = getLoginUser(request);

        // 需求 20：代客建立時，實際預約要歸屬到目標會員，不是登入的店家帳號
        String bookingUsername = user.getUsername();
        String targetMemberName = null;
        if (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) {
            try {
                var targetUser = userService.getUserEntityByUsername(forUsername);
                bookingUsername = targetUser.getUsername();
                targetMemberName = targetUser.getName();
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorMsg", "找不到指定的會員");
                return "redirect:/appointments/select-member";
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(bookingUsername));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("companySignatureImage", paymentService.getCompanySignatureImage());
            model.addAttribute("forUsername", forUsername);
            model.addAttribute("targetMemberName", targetMemberName);
            String firstError = bindingResult.getAllErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("表單資料有誤，請確認後再送出");
            model.addAttribute("errorMsg", firstError);
            return "appointments/form";
        }

        try {
            AppointmentResponse res = appointmentService.book(req, bookingUsername);
            operationLogService.log(user, "APPOINTMENT", "BOOK",
                    "預約 " + res.getAppointmentCode(),
                    targetMemberName != null ? "代客建立（" + targetMemberName + "）：" + res.getPetName() : res.getPetName());
            redirectAttributes.addFlashAttribute("successMsg",
                    targetMemberName != null ? "已為 " + targetMemberName + " 建立預約成功！" : "預約成功！");
            return "redirect:/appointments";

        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(bookingUsername));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("companySignatureImage", paymentService.getCompanySignatureImage());
            model.addAttribute("forUsername", forUsername);
            model.addAttribute("targetMemberName", targetMemberName);
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

    // ── GET /appointments/slots-manage ───────────────────────────────────
    // 需求 1：時段開關管理——查看某一天每個時段的名額上限/已預約數，
    // 可手動調整上限或直接關閉（設為 0），只影響剩餘名額，不影響已預約紀錄。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/slots-manage")
    public String slotsManage(HttpServletRequest request, Model model,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("date", date);
        model.addAttribute("slots", appointmentService.getAvailableSlots(date));
        // 需求 16：公休日設定
        model.addAttribute("isClosedToday", closedDateService.isClosed(date));
        model.addAttribute("upcomingClosedDates", closedDateService.listUpcoming());
        model.addAttribute("weeklyClosureSetting", closedDateService.getWeeklyClosureSetting());
        return "appointments/slots-manage";
    }

    // ── POST /appointments/slots-manage/weekly-closure ─────────────────────
    // 固定公休星期：設定每週固定哪幾天公休（跟單一天公休日的 ClosedDate 是兩套機制並存）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/weekly-closure")
    public String updateWeeklyClosure(HttpServletRequest request,
                                      @RequestParam(defaultValue = "false") boolean monday,
                                      @RequestParam(defaultValue = "false") boolean tuesday,
                                      @RequestParam(defaultValue = "false") boolean wednesday,
                                      @RequestParam(defaultValue = "false") boolean thursday,
                                      @RequestParam(defaultValue = "false") boolean friday,
                                      @RequestParam(defaultValue = "false") boolean saturday,
                                      @RequestParam(defaultValue = "false") boolean sunday,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      RedirectAttributes ra) {
        User user = getLoginUser(request);
        closedDateService.updateWeeklyClosureSetting(monday, tuesday, wednesday, thursday, friday, saturday, sunday);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_WEEKLY_CLOSURE", "固定公休星期設定", null);
        ra.addFlashAttribute("successMsg", "固定公休星期已更新");
        return "redirect:/appointments/slots-manage" + (date != null ? "?date=" + date : "");
    }

    // ── POST /appointments/slots-manage/closed-date/add ───────────────────
    // 需求 16：將指定日期設為公休日，當天所有時段自動不開放預約
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/closed-date/add")
    public String addClosedDate(HttpServletRequest request,
                                @RequestParam
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String reason,
                                RedirectAttributes ra) {
        User user = getLoginUser(request);
        closedDateService.setClosed(date, reason);
        operationLogService.log(user, "APPOINTMENT", "SET_CLOSED_DATE",
                date.toString(), reason == null || reason.isBlank() ? "公休" : reason);
        ra.addFlashAttribute("successMsg", date + " 已設定為公休日");
        return "redirect:/appointments/slots-manage?date=" + date;
    }

    // ── POST /appointments/slots-manage/closed-date/remove ────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/closed-date/remove")
    public String removeClosedDate(HttpServletRequest request,
                                   @RequestParam
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   RedirectAttributes ra) {
        User user = getLoginUser(request);
        closedDateService.removeClosed(date);
        operationLogService.log(user, "APPOINTMENT", "UNSET_CLOSED_DATE", date.toString(), "取消公休");
        ra.addFlashAttribute("successMsg", date + " 已取消公休設定");
        return "redirect:/appointments/slots-manage?date=" + date;
    }

    // ── POST /appointments/slots-manage/update ───────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/slots-manage/update")
    public String updateSlotCapacity(HttpServletRequest request,
                                     @RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) java.time.LocalTime time,
                                     @RequestParam int capacity,
                                     RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            slotCapacityService.setCapacity(date, time, capacity);
            operationLogService.log(user, "APPOINTMENT", "SET_SLOT_CAPACITY",
                    date + " " + time, "調整為 " + capacity + " 位");
            ra.addFlashAttribute("successMsg",
                    date + " " + time + " 時段名額已調整為 " + capacity + " 位");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "調整失敗：" + e.getMessage());
        }
        return "redirect:/appointments/slots-manage?date=" + date;
    }
}
