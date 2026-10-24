package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.CheckoutPreviewService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.StoreSettingsService;
import com.petgrooming.pet_system.service.PendingOperatorMatrixService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderCheckService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.WalkInOrderOperatorService;
import com.petgrooming.pet_system.service.WalkInPaymentService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 需求 5 / 6：現場開單店家後台頁面。
 * （對應的 REST API 在 WalkInOrderController，這裡是給店家在瀏覽器上直接操作用的表單頁面）
 */
@Controller
@RequestMapping("/admin/walk-in-orders")
@RequiredArgsConstructor
public class WalkInOrderMvcController {

    private final WalkInOrderService walkInOrderService;
    private final WalkInOrderCheckService walkInOrderCheckService;
    private final WalkInOrderOperatorService walkInOrderOperatorService;
    private final WalkInPaymentService walkInPaymentService;
    private final GroomingService groomingService;
    private final UserService userService;
    private final OperationLogService operationLogService;
    private final CheckoutPreviewService checkoutPreviewService; // 需求 15：儲值金折扣預覽
    private final StoreSettingsService storeSettingsService;
    private final RetailProductService retailProductService; // 需求 7-1
    private final PendingOperatorMatrixService pendingOperatorMatrixService; // 需求（追加）：矩陣式待補經手人
    private final GroomingMenuFilter groomingMenuFilter;

    // ── GET /admin/walk-in-orders ───────────────────────────────────────────
    // 開單表單 + 交易紀錄列表 + 待補經手人清單
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String page(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("groomingItems", groomingService.getAllItems()); // 含不可線上預約的調理項目
        // 需求 7-1：開單當下可加購商品——這裡要塞進 JS 變數（Thymeleaf inline javascript 用 Jackson 序列化），
        // 不能直接丟整個 RetailProduct entity，裡面的 createdAt（LocalDateTime）Jackson 預設不認得，
        // 會直接讓整頁渲染中斷（跟 staffList 同樣的處理方式，只留 JS 真正需要的欄位）。
        model.addAttribute("retailProducts", retailProductService.listActive().stream()
                .map(p -> Map.of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "price", p.getPrice(),
                        "stockQuantity", p.getStockQuantity()))
                .toList());
        // 需求：經手人改綁員工帳號，前端下拉選單資料來源（STAFF + ADMIN，店主可能也會親自美容）
        // ⚠️ 絕不能把 User entity 整包丟進頁面（含密碼雜湊），只給 id + 姓名
        List<User> staffAndAdmin = new ArrayList<>(userService.getAllStaffEntities());
        staffAndAdmin.addAll(userService.getAllAdminEntities());
        model.addAttribute("staffList", staffAndAdmin.stream()
                .map(s -> Map.of("id", s.getId(), "name", s.getName()))
                .toList());
        model.addAttribute("orders", walkInOrderService.listAll());
        // 需求（追加）：待補經手人改成矩陣式表單，取代原本一列一項目的清單
        model.addAttribute("walkInMatrixRows", pendingOperatorMatrixService.buildWalkInMatrix());
        model.addAttribute("appointmentMatrixRows", pendingOperatorMatrixService.buildAppointmentMatrix());
        model.addAttribute("matrixColumns", PendingOperatorMatrixService.COLUMNS);
        model.addAttribute("pointsReport", walkInOrderOperatorService.operatorPointsReport());
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
                         @RequestParam(required = false) List<String> operatorStaffIds2, // 需求（2026-10-02）
                         @RequestParam(required = false) List<String> retailProductIds,
                         @RequestParam(required = false) List<String> retailQuantities, RedirectAttributes ra,
                         @CurrentUser User user) {
        try {
            WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
            req.setMemberUsername(memberUsername != null && !memberUsername.isBlank() ? memberUsername : null);
            req.setPetName(petName);
            req.setNote(note);

            req.setItems(WalkInOrderForm.serviceItems(itemCodes, operatorStaffIds, operatorStaffIds2));
            req.setRetailItems(retailItems(retailProductIds, retailQuantities));

            var result = walkInOrderService.create(req, user.getUsername());
            operationLogService.log(user, "WALKIN", "CREATE", "現場單 #" + result.getId(),
                    "$" + result.getTotalAmount() + (petName != null && !petName.isBlank() ? "（" + petName + "）" : ""));
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
                               RedirectAttributes ra,
                               @CurrentUser User user) {
        try {
            walkInOrderOperatorService.fillOperator(itemId, staffId);
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
                                    @RequestParam(value = "staffIds2", required = false) List<String> staffIds2, RedirectAttributes ra,
                                    @CurrentUser User user) {
        int filled = 0;
        for (int i = 0; i < itemIds.size(); i++) {
            String staffIdStr = i < staffIds.size() ? staffIds.get(i) : null;
            if (staffIdStr == null || staffIdStr.isBlank()) continue; // 這列沒選就跳過，維持待補狀態
            try {
                Long staffId = Long.valueOf(staffIdStr);
                String s2 = staffIds2 != null && i < staffIds2.size() ? staffIds2.get(i) : null;
                walkInOrderOperatorService.fillOperator(itemIds.get(i), staffId,
                        s2 != null && !s2.isBlank() ? Long.valueOf(s2) : null);
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

    /**
     * 需求 7-1：開單當下也能直接加零售商品，支援純零售訂單（沒有任何美容服務項目）。
     * 商品空白的列跳過，數量空白視為 1。
     */
    private static List<WalkInOrderCreateRequest.RetailItem> retailItems(List<String> retailProductIds,
                                                                         List<String> retailQuantities) {
        List<WalkInOrderCreateRequest.RetailItem> retailItems = new ArrayList<>();
        if (retailProductIds == null) return retailItems;
        for (int i = 0; i < retailProductIds.size(); i++) {
            String idStr = retailProductIds.get(i);
            if (idStr == null || idStr.isBlank()) continue;
            String qtyStr = retailQuantities != null && i < retailQuantities.size() ? retailQuantities.get(i) : "1";
            WalkInOrderCreateRequest.RetailItem retailItem = new WalkInOrderCreateRequest.RetailItem();
            retailItem.setRetailProductId(Long.valueOf(idStr));
            retailItem.setQuantity(qtyStr == null || qtyStr.isBlank() ? 1 : Integer.parseInt(qtyStr));
            retailItems.add(retailItem);
        }
        return retailItems;
    }

    // ── GET /admin/walk-in-orders/{id}/checkout ──────────────────────────
    // 需求 15：現場開單結帳畫面統一——跟「預約訂單結帳」同樣風格的專屬結帳頁，
    // 結帳前先看到逐項目折扣預覽（回洗優惠／會員折扣，兩者擇一），不是原本表格列裡的小下拉選單直接送出。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        try {
            var order = walkInOrderService.getById(id);
            model.addAttribute("order", order);
            model.addAttribute("retailProducts", retailProductService.listActive()); // 需求 7-1：加購商品清單
            // 需求（追加）：僅限既有客戶／適用物種，這隻寵物不符合資格的項目直接從選單濾掉
            boolean isExisting = walkInOrderService.isExistingCustomerPet(id);
            String petType = walkInOrderService.getPetTypeForOrder(id);
            var pet = walkInOrderService.getPetForOrder(id);
            model.addAttribute("pet", pet);
            // 需求（追加，2026-08-26）：自訂金額加購用的積分分類下拉選單
            model.addAttribute("performanceCategories", PerformanceCategory.values());
            // 需求（追加，2026-08-24 修正）：先抓一份完整、沒被篩過的服務項目清單，
            // 「新增服務項目」下拉選單的篩選跟「消費明細補上體重級距標記」都從這份
            // 完整清單衍生，不要各自分開查——原本的 bug 就是兩邊各自查了不同時機/
            // 條件的清單，導致這張單已經選定的項目沒辦法反查回自己的體重級距。
            var allGroomingItems = groomingService.getAllItems();
            Map<Long, String> tierByItemId = allGroomingItems.stream()
                    .filter(i -> i.getDogWeightTier() != null)
                    .collect(Collectors.toMap(
                            GroomingItemResponse::getId,
                            GroomingItemResponse::getDogWeightTier));
            // 幫這張單消費明細裡的每一項目，直接標上自己的體重級距（如果有的話），
            // 前端「鎖定為固定套餐」按鈕靠這個欄位判斷，不用再跟下拉選單那份清單比對。
            if (order.getItems() != null) {
                order.getItems().forEach(item -> {
                    if (item.getGroomingItemId() != null) {
                        item.setDogWeightTier(tierByItemId.get(item.getGroomingItemId()));
                    }
                });
            }

            // 「新增服務項目」選單依這隻寵物的體型篩選（規則見 GroomingMenuFilter）；非會員、沒有寵物資料時只依物種篩選
            model.addAttribute("groomingItems", pet != null
                    ? groomingMenuFilter.filterForPetShape(allGroomingItems, isExisting, petType, pet)
                    : groomingMenuFilter.filterForOptionalPetType(allGroomingItems, isExisting, petType));
            model.addAttribute("weightMissing", groomingMenuFilter.needsWeight(petType, pet));
            model.addAttribute("bankAccountInfo",
                    storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT)); // 需求 15 修正
            // 需求 10：跟預約結帳一致，不提供「信用卡」選項；需求 15 修正：匯款有待對帳流程，保留在選單
            model.addAttribute("paymentMethods", CheckoutPageSupport.CHECKOUT_PAYMENT_METHODS);

            if (order.getMemberUsername() != null) {
                CheckoutPageSupport.addWalletPreview(model, checkoutPreviewService.forWalkIn(
                        order.getMemberUsername(), order.getItems(), petType));
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        return "admin/walk-in-order-checkout";
    }

    // ── POST /admin/walk-in-orders/{id}/add-retail-item ─────────────────
    // 需求 7-1：結帳頁直接加購零售商品
    // 需求（追加）：新增 from 參數，讓核對頁提交後也能導回核對頁，不會固定跳回結帳頁
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/add-retail-item")
    public String addRetailItem(@PathVariable Long id,
                                @RequestParam Long retailProductId,
                                @RequestParam(defaultValue = "1") int quantity,
                                @RequestParam(defaultValue = "checkout") String from,
                                RedirectAttributes ra,
                                @CurrentUser User user) {
        try {
            walkInOrderService.addRetailItem(id, retailProductId, quantity, user.getUsername());
            ra.addFlashAttribute("successMsg", "已加購商品");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "加購失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders/" + id + "/" + from;
    }

    // ── POST /admin/walk-in-orders/{id}/add-grooming-item ────────────────
    // 需求（追加）：編輯訂單——結帳前補一筆漏開/開錯的美容服務項目
    // 需求（追加，2026-08-26）：customPrice 選填，店員手動輸入自訂價格用
    // （例如剪毛這種價格浮動的項目），空白就照項目原本的固定價格。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/add-grooming-item")
    public String addGroomingItem(@PathVariable Long id,
                                  @RequestParam Long groomingItemId,
                                  @RequestParam(required = false) Integer customPrice,
                                  @RequestParam(required = false) Long operatorStaffId, // 需求（2026-10-02）：空白＝稍後補填
                                  @RequestParam(required = false) Long operatorStaffId2,
                                  @RequestParam(defaultValue = "checkout") String from,
                                  RedirectAttributes ra,
                                  @CurrentUser User user) {
        try {
            walkInOrderService.addGroomingItem(id, groomingItemId, customPrice, operatorStaffId, operatorStaffId2, user.getUsername());
            ra.addFlashAttribute("successMsg", "已新增項目");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "新增失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders/" + id + "/" + from;
    }

    // ── POST /admin/walk-in-orders/{id}/add-custom-item ──────────────────
    // 需求（追加，2026-08-26）：自訂金額加購——處理「高階定制調理」開放式報價
    // 跟各種浮動加價，不綁定任何現有服務項目，店員直接輸入項目名稱+金額。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/add-custom-item")
    public String addCustomItem(@PathVariable Long id,
                                @RequestParam String itemName,
                                @RequestParam int price,
                                @RequestParam(required = false) PerformanceCategory category,
                                @RequestParam(required = false) Long operatorStaffId,
                                @RequestParam(required = false) Long operatorStaffId2,
                                @RequestParam(defaultValue = "checkout") String from,
                                RedirectAttributes ra,
                                @CurrentUser User user) {
        try {
            walkInOrderService.addCustomItem(id, itemName, price, category, operatorStaffId, operatorStaffId2, user.getUsername());
            ra.addFlashAttribute("successMsg", "已新增自訂項目「" + itemName + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "新增失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders/" + id + "/" + from;
    }

    // ── POST /admin/walk-in-orders/{id}/remove-item ──────────────────────
    // 需求（追加）：從「只能移除零售商品」放寬成「結帳前任何項目都能移除」，
    // 路由名稱同步從 remove-retail-item 改成更通用的 remove-item。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/remove-item")
    public String removeItem(@PathVariable Long id,
                             @RequestParam Long itemId,
                             @RequestParam(defaultValue = "checkout") String from,
                             RedirectAttributes ra,
                             @CurrentUser User user) {
        try {
            walkInOrderService.removeItem(id, itemId, user.getUsername());
            ra.addFlashAttribute("successMsg", "已移除項目");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "移除失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders/" + id + "/" + from;
    }

    // ── POST /admin/walk-in-orders/{id}/checkout ────────────────────────────
    // 需求：現場單串接結帳
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/checkout")
    public String checkout(@PathVariable Long id,
                           @RequestParam PaymentMethod paymentMethod, RedirectAttributes ra,
                           @CurrentUser User user) {
        try {
            var result = walkInPaymentService.checkout(id, paymentMethod, user.getUsername());
            operationLogService.log(user, "WALKIN", "CHECKOUT", "現場單 #" + result.getId(),
                    result.getPaymentMethodLabel());
            ra.addFlashAttribute("successMsg",
                    "結帳完成！單號 #" + result.getId() + "，付款方式：" + result.getPaymentMethodLabel());

            CheckoutPageSupport.addWeightUpdateReminder(ra, walkInOrderService.getPetForOrder(id));

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
    public String confirmWireTransfer(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        try {
            walkInPaymentService.confirmWireTransferPayment(id, user.getUsername());
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
    public String finalCheckForm(@PathVariable Long id, Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        // 需求（追加）：僅限既有客戶／適用物種，這隻寵物不符合資格的項目直接從選單濾掉
        boolean isExisting = walkInOrderService.isExistingCustomerPet(id);
        String petType = walkInOrderService.getPetTypeForOrder(id);
        model.addAttribute("groomingItems", groomingService.getAllItems().stream()
                .filter(i -> isExisting || !i.isRequiresExistingCustomer())
                .filter(i -> i.getApplicablePetType() == null || petType == null || i.getApplicablePetType().equalsIgnoreCase(petType))
                .toList()); // 需求（追加）：核對頁也能直接編輯訂單項目
        model.addAttribute("retailProducts", retailProductService.listActive());
        // 需求（追加，2026-08-26）：自訂金額加購用的積分分類下拉選單
        model.addAttribute("performanceCategories", PerformanceCategory.values());
        try {
            var orderForCheck = walkInOrderService.getById(id);
            model.addAttribute("order", orderForCheck);
            // 需求（2026-09-29）：非會員單沒有毛孩檔案，不開放核對照片
            model.addAttribute("photoEnabled", orderForCheck.getMemberUsername() != null
                    && !orderForCheck.getMemberUsername().isBlank());
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
        }
        model.addAttribute("staffList", userService.operatorOptions()); // 需求（2026-10-02）：核對加項目可選經手人
        return "admin/walk-in-order-final-check";
    }

    // ── POST /admin/walk-in-orders/{id}/end-service ─────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/end-service")
    public String endService(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        try {
            walkInOrderCheckService.endService(id, user.getUsername());
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
                             @RequestParam(required = false) List<String> photoUrls,
                             @RequestParam(required = false) List<String> photoPublicIds, RedirectAttributes ra,
                             @CurrentUser User user) {
        try {
            walkInOrderCheckService.finalCheck(id, note, signatureData, user.getUsername(), photoUrls, photoPublicIds);
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
    public String refund(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        try {
            walkInPaymentService.refund(id, user.getUsername());
            operationLogService.log(user, "WALKIN", "REFUND", "現場單 #" + id, null);
            ra.addFlashAttribute("successMsg", "已退款並刪除此筆現場單，請重新開單");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "退款失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }
}
