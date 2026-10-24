package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.MobileWalkInViews;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.StoreSettingsService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderCheckService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.WalkInOrderOperatorService;
import com.petgrooming.pet_system.service.WalkInPaymentService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 員工手機版的現場單流程：明細、結束服務／匯款確認／退款、補經手人、核對、改項目、結帳。
 */
@Controller
@RequestMapping("/m/walk-in")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileWalkInOrderController {

    private final WalkInOrderService walkInOrderService;
    private final WalkInOrderCheckService walkInOrderCheckService;
    private final WalkInOrderOperatorService walkInOrderOperatorService;
    private final WalkInPaymentService walkInPaymentService;
    private final UserService userService;
    private final GroomingService groomingItemService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final RetailProductService retailProductService;
    private final StoreSettingsService storeSettingsService;
    private final WalletService walletService;
    private final OperationLogService operationLogService;
    private final MobileViewHelper view;
    private final MobileWalkInViews walkInViews; // 列表列、流程階段、搜尋客人、建議套餐

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

    // ── 現場單明細 ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        WalkInOrderResponse o;
        try {
            o = walkInOrderService.getById(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/m/walk-in";
        }
        PetResponse pet = safePet(id);
        String stage = walkInViews.stageKey(o);

        model.addAttribute("user", user);
        model.addAttribute("o", o);
        model.addAttribute("row", walkInViews.toRow(o));
        model.addAttribute("pet", pet);
        model.addAttribute("petSpecies", pet != null && pet.getPetType() != null ? view.speciesKey(pet.getPetType().name()) : "other");
        model.addAttribute("lines", view.fromWalkIn(o.getItems(), false));
        model.addAttribute("stage", stage);
        model.addAttribute("staffOptions", view.staffOptions());
        model.addAttribute("activeTab", "walkin");
        return "m/walk-in-detail";
    }

    // ── 狀態變更 ────────────────────────────────────────────────────────
    @PostMapping("/{id}/end-service")
    public String endService(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        return runAction(id, request, ra, "END_SERVICE", "已結束服務，已通知家長來接",
                u -> walkInOrderCheckService.endService(id, u.getUsername()), "/m/walk-in/" + id);
    }

    @PostMapping("/{id}/confirm-wire")
    public String confirmWire(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        return runAction(id, request, ra, "CONFIRM_WIRE_TRANSFER", "已確認收款",
                u -> walkInPaymentService.confirmWireTransferPayment(id, u.getUsername()), "/m/walk-in/" + id);
    }

    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        // 退款會直接刪除這張現場單，成功後回列表
        return runAction(id, request, ra, "REFUND", "已退款並刪除這張現場單",
                u -> walkInPaymentService.refund(id, u.getUsername()), "/m/walk-in");
    }

    @PostMapping("/{id}/items/{itemId}/operator")
    public String fillOperator(@PathVariable Long id, @PathVariable Long itemId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long staffId2, // 第二經手人
             RedirectAttributes ra,
             @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (staffId == null) {
            ra.addFlashAttribute("toastError", "請選擇經手人");
            return "redirect:/m/walk-in/" + id;
        }
        try {
            walkInOrderOperatorService.fillOperator(itemId, staffId, staffId2);
            operationLogService.log(user, "WALKIN", "FILL_OPERATOR", "項目 #" + itemId,
                    "指定經手人 #" + staffId + "（手機版）");
            ra.addFlashAttribute("toast", "已補填經手人");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "補填失敗：" + e.getMessage());
        }
        return "redirect:/m/walk-in/" + id;
    }

    // ── 核對（共用 m/final-check 樣板）──────────────────────────────────
    @GetMapping("/{id}/final-check")
    public String finalCheckForm(@PathVariable Long id, Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        WalkInOrderResponse o;
        try {
            o = walkInOrderService.getById(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/m/walk-in";
        }
        if (o.isPaid() || !o.isRequiresServiceFlow() || !o.isServiceEndedDone() || o.isFinalCheckDone()) {
            return "redirect:/m/walk-in/" + id;
        }
        boolean isExisting = walkInOrderService.isExistingCustomerPet(id);
        String petType = walkInOrderService.getPetTypeForOrder(id);
        Map<String, Object> row = walkInViews.toRow(o);

        model.addAttribute("user", user);
        model.addAttribute("base", "/m/walk-in/" + id);
        model.addAttribute("backUrl", "/m/walk-in/" + id);
        model.addAttribute("subtitle", row.get("title") + "，現場單 #" + id);
        model.addAttribute("avSpecies", view.speciesKey(petType));
        model.addAttribute("avInitial", row.get("initial"));
        model.addAttribute("lines", view.fromWalkIn(o.getItems(), false));
        model.addAttribute("linesTotal", o.getItems().stream().mapToInt(WalkInOrderResponse.ItemLine::getPrice).sum());
        model.addAttribute("groomingItems", groomingMenuFilter.filterForOptionalPetType(
                groomingItemService.getAllItems(), isExisting, petType));
        model.addAttribute("retailProducts", retailProductService.listActive());
        model.addAttribute("performanceCategories", PerformanceCategory.values());
        model.addAttribute("staffOptions", view.staffOptions()); // 核對加項目可選經手人
        model.addAttribute("activeTab", "walkin");
        // 非會員單沒有毛孩檔案，不開放核對照片
        model.addAttribute("photoEnabled", o.getMemberUsername() != null && !o.getMemberUsername().isBlank());
        return "m/final-check";
    }

    @PostMapping("/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String signatureData,
            @RequestParam(required = false) List<String> photoUrls,
            @RequestParam(required = false) List<String> photoPublicIds, RedirectAttributes ra,
            @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            walkInOrderCheckService.finalCheck(id, note, signatureData, user.getUsername(), photoUrls, photoPublicIds);
            operationLogService.log(user, "WALKIN", "FINAL_CHECK", "現場單 #" + id,
                    (note == null ? "" : note) + "（手機版）");
            ra.addFlashAttribute("toast", "核對完成，可以結帳了");
            return "redirect:/m/walk-in/" + id + "/checkout";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
            ra.addFlashAttribute("draftNote", note);
            ra.addFlashAttribute("draftPhotoUrls", photoUrls == null ? "" : String.join("|", photoUrls));
            ra.addFlashAttribute("draftPhotoIds", photoPublicIds == null ? "" : String.join("|", photoPublicIds));
            return "redirect:/m/walk-in/" + id + "/final-check";
        }
    }

    // ── 核對／結帳頁共用：改項目 ───────────────────────────────────────
    @PostMapping("/{id}/items/grooming")
    public String addGroomingItem(@PathVariable Long id, @RequestParam Long groomingItemId,
            @RequestParam(required = false) Integer customPrice,
            @RequestParam(required = false) Long operatorStaffId, // 空白＝稍後補填
            @RequestParam(required = false) Long operatorStaffId2,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已加入項目",
                u -> walkInOrderService.addGroomingItem(id, groomingItemId, customPrice, operatorStaffId, operatorStaffId2, u.getUsername()));
    }

    @PostMapping("/{id}/items/custom")
    public String addCustomItem(@PathVariable Long id, @RequestParam String itemName,
            @RequestParam int price,
            @RequestParam(required = false) PerformanceCategory category,
            @RequestParam(required = false) Long operatorStaffId,
            @RequestParam(required = false) Long operatorStaffId2,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已加入「" + itemName + "」",
                u -> walkInOrderService.addCustomItem(id, itemName, price, category, operatorStaffId, operatorStaffId2, u.getUsername()));
    }

    @PostMapping("/{id}/items/retail")
    public String addRetailItem(@PathVariable Long id, @RequestParam Long retailProductId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已加入商品",
                u -> walkInOrderService.addRetailItem(id, retailProductId, quantity, u.getUsername()));
    }

    @PostMapping("/{id}/items/{itemId}/remove")
    public String removeItem(@PathVariable Long id, @PathVariable Long itemId,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已移除項目",
                u -> walkInOrderService.removeItem(id, itemId, u.getUsername()));
    }

    // ── 結帳（共用 m/checkout 樣板）─────────────────────────────────────
    @GetMapping("/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        WalkInOrderResponse o;
        try {
            o = walkInOrderService.getById(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/m/walk-in";
        }
        boolean canCheckout = !o.isPaid() && !o.isPendingWireTransfer()
                && (!o.isRequiresServiceFlow() || o.isFinalCheckDone());
        if (!canCheckout) {
            return "redirect:/m/walk-in/" + id;
        }
        PetResponse pet = safePet(id);
        Map<String, Object> row = walkInViews.toRow(o);
        Integer walletAmount = walkInPaymentService.previewWalletAmount(id);

        model.addAttribute("user", user);
        model.addAttribute("base", "/m/walk-in/" + id);
        model.addAttribute("backUrl", "/m/walk-in/" + id);
        model.addAttribute("subtitle", row.get("title") + "，現場單 #" + id);
        model.addAttribute("avSpecies", pet != null ? view.speciesKey(pet.getPetType().name()) : "other");
        model.addAttribute("avInitial", row.get("initial"));
        model.addAttribute("lines", view.fromWalkIn(o.getItems(), true));
        model.addAttribute("baseAmount", o.getTotalAmount());
        model.addAttribute("standardAmount", walkInPaymentService.previewStandardAmount(id));
        model.addAttribute("walletAmount", walletAmount);
        model.addAttribute("walletEnough", false);
        model.addAttribute("walletBalance", 0);
        if (walletAmount != null && o.getMemberUsername() != null) {
            var wallet = walletService.getWallet(o.getMemberUsername());
            int balance = wallet.getBalance() == null ? 0 : wallet.getBalance();
            model.addAttribute("walletBalance", balance);
            model.addAttribute("walletEnough", balance >= walletAmount);
            model.addAttribute("walletDiscountLabel", wallet.isCardActive() && wallet.getDiscount() < 1.0
                    ? wallet.getCardTierLabel() + " " + view.formatDiscount(wallet.getDiscount()) : null);
        }
        model.addAttribute("bank", storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
        model.addAttribute("retailProducts", retailProductService.listActive());

        // 鎖定固定套餐：狗狗、還沒鎖定、單子裡有帶體重級距的套餐項目
        // （跟網頁版現場單結帳頁同一套判斷，體重級距從完整服務項目清單反查）
        if (pet != null && pet.getPetType() != null && "DOG".equalsIgnoreCase(pet.getPetType().name())
                && pet.getLockedGroomingItemId() == null) {
            Map<Long, String> tierById = groomingItemService.getAllItems().stream()
                    .filter(i -> i.getDogWeightTier() != null)
                    .collect(Collectors.toMap(GroomingItemResponse::getId, GroomingItemResponse::getDogWeightTier));
            o.getItems().stream()
                    .filter(it -> it.getGroomingItemId() != null && tierById.containsKey(it.getGroomingItemId()))
                    .findFirst()
                    .ifPresent(it -> {
                        model.addAttribute("lockPetId", pet.getId());
                        model.addAttribute("lockItemId", it.getGroomingItemId());
                        model.addAttribute("lockItemName", it.getItemName());
                    });
        }
        model.addAttribute("activeTab", "walkin");
        return "m/checkout";
    }

    @PostMapping("/{id}/checkout")
    public String checkoutSubmit(@PathVariable Long id,
            @RequestParam(required = false) PaymentMethod paymentMethod, RedirectAttributes ra,
            @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (paymentMethod == null || paymentMethod == PaymentMethod.CREDIT_CARD) {
            ra.addFlashAttribute("toastError", "請選擇付款方式");
            return "redirect:/m/walk-in/" + id + "/checkout";
        }
        try {
            var result = walkInPaymentService.checkout(id, paymentMethod, user.getUsername());
            operationLogService.log(user, "WALKIN", "CHECKOUT", "現場單 #" + id,
                    result.getPaymentMethodLabel() + "（手機版）");

            // 跟網頁版一樣：狗狗還沒鎖定固定套餐的話，結帳完提醒更新體重
            CheckoutPageSupport.addWeightUpdateReminder(ra, safePet(id));
            ra.addFlashAttribute("toast", result.isPaid()
                    ? "結帳完成"
                    : "已送出，等收到匯款後記得按「確認已收到匯款」");
            return "redirect:/m/walk-in/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "結帳失敗：" + e.getMessage());
            return "redirect:/m/walk-in/" + id + "/checkout";
        }
    }

    // ────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface StaffAction {
        void run(User user);
    }

    private String runAction(Long id, HttpServletRequest request, RedirectAttributes ra,
            String logAction, String okMsg, StaffAction action, String okTarget) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            action.run(user);
            operationLogService.log(user, "WALKIN", logAction, "現場單 #" + id, "手機版");
            ra.addFlashAttribute("toast", okMsg);
            return "redirect:" + okTarget;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "操作失敗：" + e.getMessage());
            return "redirect:/m/walk-in/" + id;
        }
    }

    private String editItems(Long id, String from, HttpServletRequest request,
            RedirectAttributes ra, String okMsg, StaffAction action) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            action.run(user);
            ra.addFlashAttribute("toast", okMsg);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
        }
        return "check".equals(from)
                ? "redirect:/m/walk-in/" + id + "/final-check"
                : "redirect:/m/walk-in/" + id + "/checkout";
    }

    private PetResponse safePet(Long orderId) {
        try {
            return walkInOrderService.getPetForOrder(orderId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
