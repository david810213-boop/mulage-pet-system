package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求 5 / 6：現場開單店家後台頁面。
 * （對應的 REST API 在 WalkInOrderController，這裡是給店家在瀏覽器上直接操作用的表單頁面）
 */
@Controller
@RequestMapping("/admin/walk-in-orders")
@RequiredArgsConstructor
public class WalkInOrderMvcController {

    private final WalkInOrderService walkInOrderService;
    private final GroomingService groomingService;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final OperationLogService operationLogService;
    private final com.petgrooming.pet_system.service.WalletService walletService; // 需求 15
    private final com.petgrooming.pet_system.service.CatRewashDiscountService catRewashDiscountService; // 需求 15
    private final com.petgrooming.pet_system.service.PaymentService paymentService; // 需求 15 修正：借用匯款帳號資訊

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    // ── GET /admin/walk-in-orders ───────────────────────────────────────────
    // 開單表單 + 交易紀錄列表 + 待補經手人清單
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String page(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("groomingItems", groomingService.getAllItems()); // 含不可線上預約的調理項目
        // 需求：經手人改綁員工帳號，前端下拉選單資料來源（STAFF + ADMIN，店主可能也會親自美容）
        // ⚠️ 絕不能把 User entity 整包丟進頁面（含密碼雜湊），只給 id + 姓名
        List<User> staffAndAdmin = new ArrayList<>(userService.getAllStaffEntities());
        staffAndAdmin.addAll(userService.getAllAdminEntities());
        model.addAttribute("staffList", staffAndAdmin.stream()
                .map(s -> java.util.Map.of("id", s.getId(), "name", s.getName()))
                .toList());
        model.addAttribute("orders", walkInOrderService.listAll());
        model.addAttribute("pendingOperatorItems", walkInOrderService.pendingOperatorItems());
        model.addAttribute("appointmentPendingItems", appointmentService.pendingItemOperators());
        model.addAttribute("pointsReport", walkInOrderService.operatorPointsReport());
        return "admin/walk-in-orders";
    }

    // ── POST /admin/walk-in-orders/create ───────────────────────────────────
    // 現場開單（表單版：itemCodes[] + operatorStaffIds[] 兩個平行陣列）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/create")
    public String create(@RequestParam(required = false) String memberUsername,
                         @RequestParam(required = false) String petName,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) List<String> itemCodes,
                         @RequestParam(required = false) List<String> operatorStaffIds,
                         HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            if (itemCodes == null || itemCodes.isEmpty()) {
                throw new IllegalArgumentException("請至少加入一個項目");
            }
            WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
            req.setMemberUsername(memberUsername != null && !memberUsername.isBlank() ? memberUsername : null);
            req.setPetName(petName);
            req.setNote(note);

            List<WalkInOrderCreateRequest.Item> items = new ArrayList<>();
            for (int i = 0; i < itemCodes.size(); i++) {
                String code = itemCodes.get(i);
                if (code == null || code.isBlank()) continue; // 表單裡沒選的空列跳過
                WalkInOrderCreateRequest.Item item = new WalkInOrderCreateRequest.Item();
                item.setItemCode(code);
                String staffIdStr = (operatorStaffIds != null && i < operatorStaffIds.size())
                        ? operatorStaffIds.get(i) : null;
                item.setOperatorStaffId((staffIdStr != null && !staffIdStr.isBlank())
                        ? Long.valueOf(staffIdStr) : null); // null → 未填寫，之後從待補清單補
                items.add(item);
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("請至少選擇一個有效項目");
            }
            req.setItems(items);

            var result = walkInOrderService.create(req, user.getUsername());
            operationLogService.log(user, "WALKIN", "CREATE", "現場單 #" + result.getId(),
                    "$" + result.getTotalAmount() + (petName != null ? "（" + petName + "）" : ""));
            ra.addFlashAttribute("successMsg",
                    "開單成功！單號 #" + result.getId() + "，總額 $" + result.getTotalAmount());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "開單失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /admin/walk-in-orders/items/{itemId}/operator ─────────────────
    // 需求 6：補填經手人（綁定員工帳號）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/items/{itemId}/operator")
    public String fillOperator(@PathVariable Long itemId,
                               @RequestParam Long staffId,
                               HttpServletRequest request,
                               RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            walkInOrderService.fillOperator(itemId, staffId);
            operationLogService.log(user, "WALKIN", "FILL_OPERATOR",
                    "項目 #" + itemId, "指定經手人 #" + staffId);
            ra.addFlashAttribute("successMsg", "已補填經手人");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "補填失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /admin/walk-in-orders/items/operator/batch ──────────────────
    // 需求 11：批次補填經手人，逐一勾選/選擇後，一次性儲存全部，不用每列各按一次
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/items/operator/batch")
    public String fillOperatorBatch(@RequestParam("itemIds") List<Long> itemIds,
                                    @RequestParam("staffIds") List<String> staffIds,
                                    HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        int filled = 0;
        for (int i = 0; i < itemIds.size(); i++) {
            String staffIdStr = i < staffIds.size() ? staffIds.get(i) : null;
            if (staffIdStr == null || staffIdStr.isBlank()) continue; // 這列沒選就跳過，維持待補狀態
            try {
                Long staffId = Long.valueOf(staffIdStr);
                walkInOrderService.fillOperator(itemIds.get(i), staffId);
                operationLogService.log(user, "WALKIN", "FILL_OPERATOR",
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

    // ── GET /admin/walk-in-orders/{id}/checkout ──────────────────────────
    // 需求 15：現場開單結帳畫面統一——跟「預約訂單結帳」同樣風格的專屬結帳頁，
    // 結帳前先看到逐項目折扣預覽（回洗優惠／會員折扣，兩者擇一），不是原本表格列裡的小下拉選單直接送出。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        try {
            var order = walkInOrderService.getById(id);
            model.addAttribute("order", order);
            model.addAttribute("bankAccountInfo", paymentService.getBankAccountInfo()); // 需求 15 修正

            // 需求 10：跟預約結帳一致，不再提供「信用卡」選項
            // 需求 15 修正：匯款現在有待對帳流程了，重新加回選單
            model.addAttribute("paymentMethods", java.util.Arrays.stream(
                            com.petgrooming.pet_system.enums.PaymentMethod.values())
                    .filter(m -> m != com.petgrooming.pet_system.enums.PaymentMethod.CREDIT_CARD)
                    .toList());

            if (order.getMemberUsername() != null) {
                var wallet = walletService.getWallet(order.getMemberUsername());
                model.addAttribute("walletBalance", wallet.getBalance());
                model.addAttribute("walletLowBalance", wallet.getBalance() < com.petgrooming.pet_system.service.PaymentService.WALLET_LOW_BALANCE_THRESHOLD);
                model.addAttribute("walletDiscount", wallet.getDiscount());
                model.addAttribute("walletDiscountActive", wallet.isCardActive() && wallet.getDiscount() < 1.0);

                double walletPreview = order.getItems().stream()
                        .mapToDouble(it -> catRewashDiscountService.resolvePreferredDiscount(
                                it.getPrice(), it.isRewashEligible(), it.isDiscountEligible(), wallet.getDiscount()).price())
                        .sum();
                model.addAttribute("walletFinalAmount", (int) Math.round(walletPreview));
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "admin/walk-in-order-checkout";
    }

    // ── POST /admin/walk-in-orders/{id}/checkout ────────────────────────────
    // 需求：現場單串接結帳
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/checkout")
    public String checkout(@PathVariable Long id,
                           @RequestParam com.petgrooming.pet_system.enums.PaymentMethod paymentMethod,
                           HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var result = walkInOrderService.checkout(id, paymentMethod, user.getUsername());
            operationLogService.log(user, "WALKIN", "CHECKOUT", "現場單 #" + result.getId(),
                    result.getPaymentMethodLabel());
            ra.addFlashAttribute("successMsg",
                    "結帳完成！單號 #" + result.getId() + "，付款方式：" + result.getPaymentMethodLabel());
            return "redirect:/admin/walk-in-orders";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "結帳失敗：" + e.getMessage());
            return "redirect:/admin/walk-in-orders/" + id + "/checkout";
        }
    }

    // ── POST /admin/walk-in-orders/{id}/confirm-wire-transfer ────────────
    // 需求 15 修正：店員核對匯款到帳後點擊，現場單才正式轉為已完成
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm-wire-transfer")
    public String confirmWireTransfer(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            walkInOrderService.confirmWireTransferPayment(id, user.getUsername());
            operationLogService.log(user, "WALKIN", "CONFIRM_WIRE_TRANSFER", "現場單 #" + id, null);
            ra.addFlashAttribute("successMsg", "已確認收款，現場單 #" + id + " 已完成");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "確認收款失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── GET /admin/walk-in-orders/{id}/final-check ──────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/final-check")
    public String finalCheckForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        try {
            model.addAttribute("order", walkInOrderService.getById(id));
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "admin/walk-in-order-final-check";
    }

    // ── POST /admin/walk-in-orders/{id}/end-service ─────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/end-service")
    public String endService(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            walkInOrderService.endService(id, user.getUsername());
            operationLogService.log(user, "WALKIN", "END_SERVICE", "現場單 #" + id, null);
            ra.addFlashAttribute("successMsg", "已結束服務，已通知家長來店接寵物");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "操作失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /admin/walk-in-orders/{id}/final-check ─────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/final-check")
    public String finalCheck(@PathVariable Long id,
                             @RequestParam String note,
                             @RequestParam(name = "signatureData") String signatureData,
                             HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            walkInOrderService.finalCheck(id, note, signatureData, user.getUsername());
            operationLogService.log(user, "WALKIN", "FINAL_CHECK", "現場單 #" + id, note);
            ra.addFlashAttribute("successMsg", "核對完成，已可進行結帳");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "核對失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }

    // ── POST /admin/walk-in-orders/{id}/refund ───────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            walkInOrderService.refund(id, user.getUsername());
            operationLogService.log(user, "WALKIN", "REFUND", "現場單 #" + id, null);
            ra.addFlashAttribute("successMsg", "已退款並刪除此筆現場單，請重新開單");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "退款失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }
}
