package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentListService;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentCheckInService;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.AppointmentItemService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentMvcController {

    private final AppointmentService appointmentService;
    private final AppointmentCheckInService appointmentCheckInService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentItemService appointmentItemService;
    private final AppointmentListService appointmentListService;
    private final GroomingService groomingItemService;
    private final UserService userService;
    private final OperationLogService operationLogService;
    private final RetailProductService retailProductService; // 核對頁編輯訂單
    private final GroomingMenuFilter groomingMenuFilter; // 菜單篩選抽成共用元件，手機版也要用

    // ── GET /appointments ──────────────────────────────────────────────────
    // 💡 作用：查看預約清單。不貼貼紙，因為一般會員與員工登入後都能看（各自看不同範圍）
    // 支援篩選：日期區間、狀態（已確認/已取消）、付款狀態、關鍵字（預約編號/飼主姓名/寵物名稱）
    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String paidStatus,
                       @RequestParam(required = false) String keyword,
                       @CurrentUser User user) {

        model.addAttribute("user", user);

        var filter = new AppointmentListService.Filter(dateFrom, dateTo, status, paidStatus, keyword);
        if (user.isStaffOrAdmin()) {
            LocalDate today = LocalDate.now();
            var list = appointmentListService.forStaff(filter, today);
            model.addAttribute("todayCount", list.summary().todayCount());
            model.addAttribute("completedCount", list.summary().completedCount());
            model.addAttribute("todayRevenue", list.summary().todayRevenue());
            model.addAttribute("pendingCount", list.summary().pendingCount());
            model.addAttribute("today", today);
            model.addAttribute("appointments", list.appointments());
        } else {
            model.addAttribute("appointments", appointmentListService.forCustomer(user.getUsername(), filter));
        }
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("status", status);
        model.addAttribute("paidStatus", paidStatus);
        model.addAttribute("keyword", keyword);
        return "appointments/list";
    }

    // 關鍵字比對：預約編號（AP001 或純數字）、飼主姓名、寵物名稱
    // ── POST /appointments/{id}/confirm ────────────────────────────────────
    // 店家確認預約並敲定最後時間
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                          LocalDateTime confirmedTime, RedirectAttributes redirectAttributes,
                          @CurrentUser User user) {
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
    // 店家設定備注（雙可見性：internalNote 店家專用 / memberNote 會員可見）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/notes")
    public String setNotes(@PathVariable Long id,
                           @RequestParam(required = false) String internalNote,
                           @RequestParam(required = false) String memberNote, RedirectAttributes redirectAttributes,
                           @CurrentUser User user) {
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
    public String checkinOrderForm(@PathVariable Long id, Model model, @CurrentUser User user) {
        model.addAttribute("user", user);

        var target = appointmentQueryService.getForAdmin(id).orElse(null);
        if (target == null) {
            return "redirect:/appointments?status=CONFIRMED";
        }
        model.addAttribute("appointment", target);
        // 僅限既有客戶／適用物種，這隻寵物不符合資格的項目直接從選單濾掉
        boolean isExisting = appointmentQueryService.isExistingCustomerPet(id);
        // 只顯示符合這隻寵物體型的項目（狗狗體重級距、貓咪毛髮分類），規則見 GroomingMenuFilter
        var pet = appointmentQueryService.getPetForAppointment(id);
        model.addAttribute("pet", pet); // 頁面上顯示鎖定套餐狀態＋解鎖按鈕要用
        model.addAttribute("groomingItems", filterItemsForPetShape(
                groomingItemService.getAllItems(), isExisting, target.getPetType(), pet));
        model.addAttribute("weightMissing", groomingMenuFilter.needsWeight(target.getPetType(), pet));
        model.addAttribute("staffList", userService.operatorOptions()); // 開單可選經手人
        return "appointments/checkin-order";
    }

    // 套餐化——「僅限既有客戶」+「適用物種」共用的選單過濾邏輯，
    // 這個 controller 裡好幾個 GET handler 都要用，抽成一個小方法避免重複寫。
    private List<GroomingItemResponse> filterItemsFor(
            List<GroomingItemResponse> items, boolean isExisting, String petType) {
        return groomingMenuFilter.filterFor(items, isExisting, petType);
    }

    // 在 filterItemsFor() 的基礎上再依這隻寵物的體型篩選，規則見 GroomingMenuFilter.filterForPetShape()
    private List<GroomingItemResponse> filterItemsForPetShape(
            List<GroomingItemResponse> items, boolean isExisting,
            String petType, PetResponse pet) {
        return groomingMenuFilter.filterForPetShape(items, isExisting, petType, pet);
    }

    // ── POST /appointments/{id}/checkin-order ───────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/checkin-order")
    public String checkinOrderSubmit(@PathVariable Long id,
                                     @RequestParam(required = false) List<String> itemCodes,
                                     @RequestParam(required = false) List<String> operatorStaffIds,
                                     @RequestParam(required = false) List<String> operatorStaffIds2,
                                     
                                     RedirectAttributes redirectAttributes,
                                     @CurrentUser User user) {
        try {
            List<Long> opIds = null;
            if (operatorStaffIds != null) {
                opIds = new ArrayList<>();
                for (String s : operatorStaffIds) opIds.add(s != null && !s.isBlank() ? Long.valueOf(s.trim()) : null);
            }
            List<Long> opIds2 = null;
            if (operatorStaffIds2 != null) {
                opIds2 = new ArrayList<>();
                for (String s : operatorStaffIds2) opIds2.add(s != null && !s.isBlank() ? Long.valueOf(s.trim()) : null);
            }
            appointmentCheckInService.confirmCheckinOrder(id, itemCodes, opIds, opIds2, user.getUsername());
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
                                          RedirectAttributes redirectAttributes,
                                          @CurrentUser User user) {
        try {
            appointmentItemService.fillItemOperator(itemId, staffId);
            operationLogService.log(user, "APPOINTMENT", "FILL_OPERATOR",
                    "項目 #" + itemId, "指定經手人 #" + staffId);
            redirectAttributes.addFlashAttribute("successMsg", "已補填經手人");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "補填失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /appointments/checkin-items/operator/batch ──────────────────
    // 批次補填經手人（依預約編號現場開單這邊的待補清單）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/checkin-items/operator/batch")
    public String fillCheckinItemOperatorBatch(@RequestParam("itemIds") List<Long> itemIds,
                                               @RequestParam("staffIds") List<String> staffIds,
                                               @RequestParam(value = "staffIds2", required = false) List<String> staffIds2, RedirectAttributes ra,
                                               @CurrentUser User user) {
        int filled = 0;
        for (int i = 0; i < itemIds.size(); i++) {
            String staffIdStr = i < staffIds.size() ? staffIds.get(i) : null;
            if (staffIdStr == null || staffIdStr.isBlank()) continue;
            try {
                Long staffId = Long.valueOf(staffIdStr);
                String s2 = staffIds2 != null && i < staffIds2.size() ? staffIds2.get(i) : null;
                appointmentItemService.fillItemOperator(itemIds.get(i), staffId,
                        s2 != null && !s2.isBlank() ? Long.valueOf(s2) : null);
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
    public String start(@PathVariable Long id,
                        RedirectAttributes redirectAttributes,
                        @CurrentUser User user) {
        try {
            appointmentCheckInService.startProgress(id, user.getUsername());
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
    public String endService(@PathVariable Long id,
                             RedirectAttributes redirectAttributes,
                             @CurrentUser User user) {
        try {
            appointmentCheckInService.endService(id, user.getUsername());
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
    public String finalCheckForm(@PathVariable Long id, Model model, @CurrentUser User user) {
        model.addAttribute("user", user);

        var target = appointmentQueryService.getForAdmin(id).orElse(null);
        if (target == null) {
            return "redirect:/appointments?status=IN_PROGRESS";
        }
        model.addAttribute("appointment", target);
        model.addAttribute("checkinItems", appointmentItemService.getCheckinItems(id));
        // 僅限既有客戶／適用物種，這隻寵物不符合資格的項目直接從選單濾掉
        boolean isExisting = appointmentQueryService.isExistingCustomerPet(id);
        var filteredItems = filterItemsFor(groomingItemService.getAllItems(), isExisting, target.getPetType());
        model.addAttribute("groomingItems", filteredItems); // 核對頁也能直接編輯訂單項目
        model.addAttribute("retailProducts", retailProductService.listActive());
        // 自訂金額加購用的積分分類下拉選單
        model.addAttribute("performanceCategories", PerformanceCategory.values());
        model.addAttribute("staffList", userService.operatorOptions()); // 核對加項目可選經手人
        return "appointments/final-check";
    }

    // ── POST /appointments/{id}/final-check ─────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
                                   @Valid @ModelAttribute FinalCheckRequest req,
                                   BindingResult bindingResult, Model model,
                                   RedirectAttributes redirectAttributes,
                                   @CurrentUser User user) {

        if (bindingResult.hasErrors()) {
            var target = appointmentQueryService.getForAdmin(id).orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("appointment", target);
            model.addAttribute("checkinItems", appointmentItemService.getCheckinItems(id)); // 漏帶導致誤顯示「尚未開單」
            boolean isExisting1 = appointmentQueryService.isExistingCustomerPet(id);
            model.addAttribute("groomingItems", filterItemsFor(groomingItemService.getAllItems(), isExisting1, target != null ? target.getPetType() : null));
            model.addAttribute("retailProducts", retailProductService.listActive());
            model.addAttribute("performanceCategories", PerformanceCategory.values());
            model.addAttribute("errorMsg", "請完整填寫備注並完成簽名");
            model.addAttribute("staffList", userService.operatorOptions());
            return "appointments/final-check";
        }

        try {
            appointmentCheckInService.finalCheck(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "FINAL_CHECK", "預約 #" + id, req.getNote());
            redirectAttributes.addFlashAttribute("successMsg", "核對完成，已可進行結帳");
            return "redirect:/appointments?status=IN_PROGRESS";
        } catch (IllegalArgumentException e) {
            var target = appointmentQueryService.getForAdmin(id).orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("appointment", target);
            model.addAttribute("checkinItems", appointmentItemService.getCheckinItems(id)); // 同上
            boolean isExisting2 = appointmentQueryService.isExistingCustomerPet(id);
            model.addAttribute("groomingItems", filterItemsFor(groomingItemService.getAllItems(), isExisting2, target != null ? target.getPetType() : null));
            model.addAttribute("retailProducts", retailProductService.listActive());
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("performanceCategories", PerformanceCategory.values());
            model.addAttribute("staffList", userService.operatorOptions());
            return "appointments/final-check";
        }
    }

    // ── POST /appointments/{id}/cancel ─────────────────────────────────────
    // 💡 作用：取消預約（後台版本，表單提交）
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes redirectAttributes,
                         @CurrentUser User user) {
        try {
            var req = new CancelAppointmentRequest();
            req.setReason(reason);
            appointmentService.cancel(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CANCEL", "預約 #" + id, reason);
            redirectAttributes.addFlashAttribute("successMsg", "預約已取消");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "取消失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
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
