package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.MobileAppointmentCardView;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentCheckInService;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.AppointmentDetailService;
import com.petgrooming.pet_system.service.AppointmentItemService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MobileAppointmentCards;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.StoreSettingsService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 員工手機版的預約流程：預約詳情、確認／開始／結束服務／取消／匯款確認／退款、到店開單、核對、改項目、結帳。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileAppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentCheckInService appointmentCheckInService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentDetailService appointmentDetailService;
    private final AppointmentItemService appointmentItemService;
    private final UserService userService;
    private final RetailProductService retailProductService;
    private final OperationLogService operationLogService;
    private final GroomingService groomingItemService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final PaymentService paymentService;
    private final StoreSettingsService storeSettingsService;
    private final WalletService walletService;
    private final MobileViewHelper view;
    private final MobileAppointmentCards cards; // 預約卡片、流程階段與日期標籤

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

    // ── 預約詳情 ────────────────────────────────────────────────────────
    @GetMapping("/appointments/{id}")
    public String appointmentDetail(@PathVariable Long id, Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null) {
            return "redirect:/m/appointments";
        }
        PetResponse pet = null;
        try {
            pet = appointmentQueryService.getPetForAppointment(id);
        } catch (IllegalArgumentException e) {
            // 找不到寵物檔案（例如寵物已刪除）不影響顯示預約本身
        }
        MobileAppointmentCardView card = cards.toCard(a, LocalDate.now());

        model.addAttribute("user", user);
        model.addAttribute("a", a);
        model.addAttribute("card", card);
        model.addAttribute("pet", pet);
        model.addAttribute("step", cards.stepIndex(card.getStage()));
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
                user -> appointmentCheckInService.startProgress(id, user.getUsername()));
    }

    // ── 結束服務：通知家長來店接寵物 ─────────────────────────────────────
    @PostMapping("/appointments/{id}/end-service")
    public String endService(@PathVariable Long id, @RequestParam(required = false) String back,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return runAction(id, back, request, redirectAttributes, "END_SERVICE", "已結束服務，已通知家長來接",
                user -> appointmentCheckInService.endService(id, user.getUsername()));
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
    public String checkinForm(@PathVariable Long id, Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        AppointmentAdminResponse a = findAppointment(id);
        if (a == null || a.isCancelled()) {
            return "redirect:/m/appointments";
        }
        boolean isExisting = appointmentQueryService.isExistingCustomerPet(id);
        PetResponse pet = appointmentQueryService.getPetForAppointment(id);
        List<GroomingItemResponse> items = groomingMenuFilter.filterForPetShape(
                groomingItemService.getAllItems(), isExisting, a.getPetType(), pet);
        model.addAttribute("weightMissing", groomingMenuFilter.needsWeight(a.getPetType(), pet));

        // 顧客預約時選的項目，開單頁預設幫忙勾好，店員只要改有變動的部分
        Set<String> preselected = a.getSelectedItems() == null ? Set.of()
                : a.getSelectedItems().stream().map(GroomingItem::getItemCode)
                        .collect(Collectors.toSet());

        model.addAttribute("user", user);
        model.addAttribute("a", a);
        model.addAttribute("card", cards.toCard(a, LocalDate.now()));
        model.addAttribute("pet", pet);
        model.addAttribute("mainItems", items.stream()
                .filter(i -> !"OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("otherItems", items.stream()
                .filter(i -> "OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("preselected", preselected);
        model.addAttribute("staffOptions", view.staffOptions()); // 開單可選經手人
        model.addAttribute("activeTab", "appointments");
        return "m/checkin";
    }

    @PostMapping("/appointments/{id}/checkin")
    public String checkinSubmit(@PathVariable Long id,
            @RequestParam(required = false) List<String> itemCodes,
            @RequestParam(required = false) List<String> operatorStaffIds,
            @RequestParam(required = false) List<String> operatorStaffIds2, // 第二經手人
             RedirectAttributes redirectAttributes,
             @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (itemCodes == null || itemCodes.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "請至少勾選一個服務項目");
            return "redirect:/m/appointments/" + id + "/checkin";
        }
        try {
            appointmentCheckInService.confirmCheckinOrder(id, itemCodes, parseStaffIds(operatorStaffIds),
                    parseStaffIds(operatorStaffIds2), user.getUsername());
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
    public String finalCheckForm(@PathVariable Long id, Model model, @CurrentUser User user) {
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
        List<AppointmentItem> items = appointmentItemService.getCheckinItems(id);
        // 跟網頁版核對頁同一套：只篩「僅限既有客戶」＋「適用物種」，不篩體型，
        // 讓店員現場可以補任何尺寸的項目
        boolean isExisting = appointmentQueryService.isExistingCustomerPet(id);
        List<GroomingItemResponse> addable = groomingMenuFilter.filterFor(
                groomingItemService.getAllItems(), isExisting, a.getPetType());
        MobileAppointmentCardView card = cards.toCard(a, LocalDate.now());

        model.addAttribute("user", user);
        // 核對頁樣板預約、現場單共用，網址前綴跟頁首資訊由 controller 決定
        model.addAttribute("base", "/m/appointments/" + id);
        model.addAttribute("backUrl", "/m/appointments/" + id);
        model.addAttribute("subtitle", card.getPetName() + "，" + a.getOwnerName());
        model.addAttribute("avSpecies", card.getSpecies());
        model.addAttribute("avInitial", card.getPetInitial());
        model.addAttribute("lines", view.fromAppointmentItems(items));
        model.addAttribute("linesTotal", items.stream().mapToInt(AppointmentItem::getPrice).sum());
        model.addAttribute("groomingItems", addable);
        model.addAttribute("retailProducts", retailProductService.listActive());
        model.addAttribute("performanceCategories", PerformanceCategory.values());
        model.addAttribute("staffOptions", view.staffOptions()); // 核對加項目可選經手人
        model.addAttribute("activeTab", "appointments");
        model.addAttribute("photoEnabled", true); // 核對照片
        return "m/final-check";
    }

    @PostMapping("/appointments/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String signatureData,
            @RequestParam(required = false) List<String> photoUrls,
            @RequestParam(required = false) List<String> photoPublicIds, RedirectAttributes redirectAttributes,
            @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            FinalCheckRequest req = new FinalCheckRequest();
            req.setNote(note);
            req.setSignatureData(signatureData);
            req.setPhotoUrls(photoUrls);
            req.setPhotoPublicIds(photoPublicIds);
            appointmentCheckInService.finalCheck(id, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "FINAL_CHECK", "預約 #" + id,
                    (note == null ? "" : note) + "（手機版）");
            redirectAttributes.addFlashAttribute("toast", "核對完成，可以結帳了");
            return "redirect:/m/appointments/" + id + "/checkout";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", e.getMessage());
            redirectAttributes.addFlashAttribute("draftNote", note);
            // 已經上傳的照片帶回核對頁，不用重拍
            redirectAttributes.addFlashAttribute("draftPhotoUrls", joinPhotos(photoUrls));
            redirectAttributes.addFlashAttribute("draftPhotoIds", joinPhotos(photoPublicIds));
            return "redirect:/m/appointments/" + id + "/final-check";
        }
    }

    // ── 核對／結帳頁共用：改項目 ───────────────────────────────────────
    // from=check 回核對頁，其他回結帳頁（跟網頁版 redirectAfterEdit 同樣的做法）
    @PostMapping("/appointments/{id}/items/grooming")
    public String addGroomingItem(@PathVariable Long id, @RequestParam Long groomingItemId,
            @RequestParam(required = false) Integer customPrice,
            @RequestParam(required = false) Long operatorStaffId, // 空白＝稍後補填
            @RequestParam(required = false) Long operatorStaffId2,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入項目",
                user -> appointmentItemService.addGroomingItem(id, groomingItemId, customPrice, operatorStaffId, operatorStaffId2, user.getUsername()));
    }

    @PostMapping("/appointments/{id}/items/custom")
    public String addCustomItem(@PathVariable Long id, @RequestParam String itemName,
            @RequestParam int price,
            @RequestParam(required = false) PerformanceCategory category,
            @RequestParam(required = false) Long operatorStaffId,
            @RequestParam(required = false) Long operatorStaffId2,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入「" + itemName + "」",
                user -> appointmentItemService.addCustomItem(id, itemName, price, category, operatorStaffId, operatorStaffId2, user.getUsername()));
    }

    // 經手人下拉選單的值（空字串＝稍後補填）轉成 id 清單，順序跟 itemCodes 一致
    private static List<Long> parseStaffIds(List<String> raw) {
        if (raw == null) return null;
        List<Long> ids = new ArrayList<>();
        for (String s : raw) {
            ids.add(s != null && !s.isBlank() ? Long.valueOf(s.trim()) : null);
        }
        return ids;
    }

    @PostMapping("/appointments/{id}/items/retail")
    public String addRetailItem(@PathVariable Long id, @RequestParam Long retailProductId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已加入商品",
                user -> appointmentItemService.addRetailItem(id, retailProductId, quantity, user.getUsername()));
    }

    @PostMapping("/appointments/{id}/items/{itemId}/remove")
    public String removeItem(@PathVariable Long id, @PathVariable Long itemId,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return editItems(id, from, request, redirectAttributes, "已移除項目",
                user -> appointmentItemService.removeItem(id, itemId, user.getUsername()));
    }

    // ── 結帳 ────────────────────────────────────────────────────────────
    @GetMapping("/appointments/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, Model model, @CurrentUser User user) {
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

        var detail = appointmentDetailService.getAppointmentDetail(id, user.getUsername());
        var wallet = walletService.getWallet(a.getOwnerEmail());
        int balance = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int walletAmount = paymentService.previewWalletAmount(id);
        PetResponse pet = null;
        try {
            pet = appointmentQueryService.getPetForAppointment(id);
        } catch (IllegalArgumentException e) {
            // 找不到寵物檔案不影響結帳
        }
        // 可以鎖定成固定套餐的項目：狗狗、還沒鎖定、單子裡有帶體重級距的套餐項目
        var lockable = (pet != null && "dog".equals(view.speciesKey(a.getPetType())) && pet.getLockedGroomingItemId() == null)
                ? detail.getItems().stream()
                        .filter(it -> it.getGroomingItemId() != null && it.getDogWeightTier() != null)
                        .findFirst().orElse(null)
                : null;
        MobileAppointmentCardView card = cards.toCard(a, LocalDate.now());

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
        model.addAttribute("bank", storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
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
            @RequestParam(required = false) PaymentMethod paymentMethod, RedirectAttributes redirectAttributes,
            @CurrentUser User user) {
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
                CheckoutPageSupport.addWeightUpdateReminder(redirectAttributes, appointmentQueryService.getPetForAppointment(id));
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

    // 核對失敗時把已上傳的照片清單帶回頁面（用 | 串起來放在 data 屬性）
    private static String joinPhotos(List<String> list) {
        return list == null ? "" : String.join("|", list);
    }

    // 只接受站內 /m/ 開頭的路徑，避免被帶去外部網站
    private String safeBack(String back, String fallback) {
        if (back != null && back.startsWith("/m/") && !back.contains("//") && !back.contains("\\")) {
            return back;
        }
        return fallback;
    }

    private AppointmentAdminResponse findAppointment(Long id) {
        return appointmentQueryService.findForAdmin(id).orElse(null);
    }
}
