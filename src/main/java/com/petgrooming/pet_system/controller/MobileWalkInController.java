package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.MobileLineItem;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 員工手機版：現場開單（需求，2026-09-24 第四批）。
 *
 * 流程跟網頁版現場開單完全相同：開單 → 結束服務 → 核對（家長簽名）→ 結帳；
 * 純零售單（沒有美容服務項目）開單後直接結帳。後端全部呼叫既有的
 * WalkInOrderService，沒有另外實作任何業務規則。
 *
 * 跟網頁版的差異（刻意的手機版簡化）：
 * - 選客人改成「搜尋 → 點毛孩」兩步，菜單由伺服器端依毛孩體型篩好再送出
 * - 經手人改成整張單選一位（預設自己），網頁版是每個項目各選一位；
 *   需要分開記的話，可以先選「稍後補填」，再到單子明細逐項補
 */
@Controller
@RequestMapping("/m/walk-in")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileWalkInController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final WalkInOrderService walkInOrderService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PetService petService;
    private final PetRepository petRepository;
    private final GroomingService groomingItemService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final RetailProductService retailProductService;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final OperationLogService operationLogService;
    private final MobileViewHelper view;

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

    // ── 現場單列表：未結帳全部＋今天已結帳 ─────────────────────────────────
    @GetMapping({ "", "/" })
    public String list(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        LocalDate today = LocalDate.now();
        List<WalkInOrderResponse> all = walkInOrderService.listAll();
        List<Map<String, Object>> open = all.stream().filter(o -> !o.isPaid()).map(this::toRow).toList();
        List<Map<String, Object>> doneToday = all.stream()
                .filter(o -> o.isPaid() && o.getPaymentTime() != null && o.getPaymentTime().toLocalDate().isEqual(today))
                .map(this::toRow).toList();

        model.addAttribute("user", user);
        model.addAttribute("openOrders", open);
        model.addAttribute("doneToday", doneToday);
        model.addAttribute("activeTab", "walkin");
        return "m/walk-in";
    }

    // ── 開新單 ──────────────────────────────────────────────────────────
    // 沒帶 member / guest：顯示搜尋客人畫面
    // 帶 member（＋pet）或 guest=1：顯示開單表單
    @GetMapping("/new")
    public String newOrder(HttpServletRequest request, Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) String pet,
            @RequestParam(required = false) String guest) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("activeTab", "walkin");

        boolean isGuest = "1".equals(guest);
        boolean hasMember = member != null && !member.isBlank();

        if (!isGuest && !hasMember) {
            model.addAttribute("step", "pick");
            model.addAttribute("q", q == null ? "" : q.trim());
            model.addAttribute("results", searchCustomers(q));
            return "m/walk-in-new";
        }

        PetResponse selectedPet = null;
        String memberName = null;
        if (hasMember) {
            User m = userRepository.findByUsername(member).orElse(null);
            if (m == null) {
                return "redirect:/m/walk-in/new";
            }
            memberName = m.getName();
            if (pet != null && !pet.isBlank()) {
                selectedPet = petService.getMyPets(member).stream()
                        .filter(p -> pet.equals(p.getName()))
                        .findFirst().orElse(null);
            }
        }

        List<GroomingItemResponse> all = groomingItemService.getAllItems();
        // 跟網頁版開單頁一致：有選到毛孩才依物種＋體型篩選；沒選毛孩（非會員、
        // 或會員沒指定毛孩）顯示全部項目，讓店員自己判斷
        List<GroomingItemResponse> menu = selectedPet != null
                ? groomingMenuFilter.filterForPetShape(all, true, selectedPet.getPetType().name(), selectedPet)
                : all;

        model.addAttribute("step", "form");
        model.addAttribute("guest", isGuest);
        // 開單失敗時要帶回同一個客人／毛孩，先組好網址參數
        StringBuilder bq = new StringBuilder();
        if (isGuest) {
            bq.append("guest=1");
        } else {
            bq.append("member=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
            if (pet != null && !pet.isBlank()) {
                bq.append("&pet=").append(URLEncoder.encode(pet, StandardCharsets.UTF_8));
            }
        }
        model.addAttribute("backQuery", bq.toString());
        model.addAttribute("memberUsername", hasMember ? member : "");
        model.addAttribute("memberName", memberName);
        model.addAttribute("pet", selectedPet);
        model.addAttribute("petSpecies", selectedPet != null ? view.speciesKey(selectedPet.getPetType().name()) : "other");
        model.addAttribute("petInitial", selectedPet != null ? view.initial(selectedPet.getName(), "?")
                : (hasMember ? view.initial(memberName, "?") : "客"));
        model.addAttribute("mainItems", menu.stream().filter(i -> !"OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("otherItems", menu.stream().filter(i -> "OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("suggestedCode", suggestItemCode(selectedPet, all, menu));
        model.addAttribute("staffOptions", view.staffOptions());
        model.addAttribute("meId", user.getId());
        model.addAttribute("retailProducts", retailProductService.listActive().stream()
                .filter(p -> p.getStockQuantity() > 0).toList());
        return "m/walk-in-new";
    }

    @PostMapping("/create")
    public String create(HttpServletRequest request, RedirectAttributes ra,
            @RequestParam(required = false) String memberUsername,
            @RequestParam(required = false) String petName,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> itemCodes,
            @RequestParam(required = false) String operatorStaffId,
            @RequestParam(required = false) List<String> retailProductIds,
            @RequestParam(required = false) List<String> retailQuantities,
            @RequestParam(required = false) String backQuery) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        Long operatorId = (operatorStaffId == null || operatorStaffId.isBlank()) ? null : Long.valueOf(operatorStaffId);

        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(memberUsername != null && !memberUsername.isBlank() ? memberUsername : null);
        req.setPetName(petName != null && !petName.isBlank() ? petName.trim() : null);
        req.setNote(note);

        List<WalkInOrderCreateRequest.Item> items = new ArrayList<>();
        if (itemCodes != null) {
            for (String code : itemCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                WalkInOrderCreateRequest.Item item = new WalkInOrderCreateRequest.Item();
                item.setItemCode(code);
                item.setOperatorStaffId(operatorId); // null → 稍後在明細頁補填
                items.add(item);
            }
        }
        req.setItems(items);

        List<WalkInOrderCreateRequest.RetailItem> retailItems = new ArrayList<>();
        if (retailProductIds != null) {
            for (int i = 0; i < retailProductIds.size(); i++) {
                String qtyStr = retailQuantities != null && i < retailQuantities.size() ? retailQuantities.get(i) : "0";
                int qty;
                try {
                    qty = (qtyStr == null || qtyStr.isBlank()) ? 0 : Integer.parseInt(qtyStr.trim());
                } catch (NumberFormatException e) {
                    qty = 0;
                }
                if (qty <= 0) {
                    continue; // 手機版每個商品都有數量欄位，0 代表沒買
                }
                WalkInOrderCreateRequest.RetailItem r = new WalkInOrderCreateRequest.RetailItem();
                r.setRetailProductId(Long.valueOf(retailProductIds.get(i)));
                r.setQuantity(qty);
                retailItems.add(r);
            }
        }
        req.setRetailItems(retailItems);

        String backToForm = "redirect:/m/walk-in/new" + safeQuery(backQuery);
        if (items.isEmpty() && retailItems.isEmpty()) {
            ra.addFlashAttribute("toastError", "請至少選一個服務項目或零售商品");
            return backToForm;
        }
        try {
            var result = walkInOrderService.create(req, user.getUsername());
            operationLogService.log(user, "WALKIN", "CREATE", "現場單 #" + result.getId(),
                    "$" + result.getTotalAmount()
                            + (req.getPetName() != null ? "（" + req.getPetName() + "）" : "") + "（手機版）");
            ra.addFlashAttribute("toast", "開單成功，單號 #" + result.getId());
            return "redirect:/m/walk-in/" + result.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", "開單失敗：" + e.getMessage());
            return backToForm;
        }
    }

    // ── 現場單明細 ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
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
        String stage = stageKey(o);

        model.addAttribute("user", user);
        model.addAttribute("o", o);
        model.addAttribute("row", toRow(o));
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
                u -> walkInOrderService.endService(id, u.getUsername()), "/m/walk-in/" + id);
    }

    @PostMapping("/{id}/confirm-wire")
    public String confirmWire(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        return runAction(id, request, ra, "CONFIRM_WIRE_TRANSFER", "已確認收款",
                u -> walkInOrderService.confirmWireTransferPayment(id, u.getUsername()), "/m/walk-in/" + id);
    }

    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        // 退款會直接刪除這張現場單，成功後回列表
        return runAction(id, request, ra, "REFUND", "已退款並刪除這張現場單",
                u -> walkInOrderService.refund(id, u.getUsername()), "/m/walk-in");
    }

    @PostMapping("/{id}/items/{itemId}/operator")
    public String fillOperator(@PathVariable Long id, @PathVariable Long itemId,
            @RequestParam(required = false) Long staffId,
            HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (staffId == null) {
            ra.addFlashAttribute("toastError", "請選擇經手人");
            return "redirect:/m/walk-in/" + id;
        }
        try {
            walkInOrderService.fillOperator(itemId, staffId);
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
    public String finalCheckForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
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
        Map<String, Object> row = toRow(o);

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
        model.addAttribute("activeTab", "walkin");
        return "m/final-check";
    }

    @PostMapping("/{id}/final-check")
    public String finalCheckSubmit(@PathVariable Long id,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String signatureData,
            HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        try {
            walkInOrderService.finalCheck(id, note, signatureData, user.getUsername());
            operationLogService.log(user, "WALKIN", "FINAL_CHECK", "現場單 #" + id,
                    (note == null ? "" : note) + "（手機版）");
            ra.addFlashAttribute("toast", "核對完成，可以結帳了");
            return "redirect:/m/walk-in/" + id + "/checkout";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
            ra.addFlashAttribute("draftNote", note);
            return "redirect:/m/walk-in/" + id + "/final-check";
        }
    }

    // ── 核對／結帳頁共用：改項目 ───────────────────────────────────────
    @PostMapping("/{id}/items/grooming")
    public String addGroomingItem(@PathVariable Long id, @RequestParam Long groomingItemId,
            @RequestParam(required = false) Integer customPrice,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已加入項目",
                u -> walkInOrderService.addGroomingItem(id, groomingItemId, customPrice, u.getUsername()));
    }

    @PostMapping("/{id}/items/custom")
    public String addCustomItem(@PathVariable Long id, @RequestParam String itemName,
            @RequestParam int price,
            @RequestParam(required = false) PerformanceCategory category,
            @RequestParam(defaultValue = "check") String from,
            HttpServletRequest request, RedirectAttributes ra) {
        return editItems(id, from, request, ra, "已加入「" + itemName + "」",
                u -> walkInOrderService.addCustomItem(id, itemName, price, category, u.getUsername()));
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
    public String checkoutForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
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
        Map<String, Object> row = toRow(o);
        Integer walletAmount = walkInOrderService.previewWalletAmount(id);

        model.addAttribute("user", user);
        model.addAttribute("base", "/m/walk-in/" + id);
        model.addAttribute("backUrl", "/m/walk-in/" + id);
        model.addAttribute("subtitle", row.get("title") + "，現場單 #" + id);
        model.addAttribute("avSpecies", pet != null ? view.speciesKey(pet.getPetType().name()) : "other");
        model.addAttribute("avInitial", row.get("initial"));
        model.addAttribute("lines", view.fromWalkIn(o.getItems(), true));
        model.addAttribute("baseAmount", o.getTotalAmount());
        model.addAttribute("standardAmount", walkInOrderService.previewStandardAmount(id));
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
        model.addAttribute("bank", paymentService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
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
            @RequestParam(required = false) PaymentMethod paymentMethod,
            HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (paymentMethod == null || paymentMethod == PaymentMethod.CREDIT_CARD) {
            ra.addFlashAttribute("toastError", "請選擇付款方式");
            return "redirect:/m/walk-in/" + id + "/checkout";
        }
        try {
            var result = walkInOrderService.checkout(id, paymentMethod, user.getUsername());
            operationLogService.log(user, "WALKIN", "CHECKOUT", "現場單 #" + id,
                    result.getPaymentMethodLabel() + "（手機版）");

            // 跟網頁版一樣：狗狗還沒鎖定固定套餐的話，結帳完提醒更新體重
            PetResponse pet = safePet(id);
            if (pet != null && pet.getPetType() != null && "DOG".equalsIgnoreCase(pet.getPetType().name())
                    && pet.getLockedGroomingItemId() == null) {
                ra.addFlashAttribute("weightReminderPetId", pet.getId());
                ra.addFlashAttribute("weightReminderPetName", pet.getName());
                ra.addFlashAttribute("weightReminderCurrentWeight", pet.getWeight());
            }
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

    // 流程階段，判斷條件跟網頁版現場單列表的按鈕顯示一致
    private String stageKey(WalkInOrderResponse o) {
        if (o.isPaid()) return "done";
        if (o.isPendingWireTransfer()) return "wire";
        if (!o.isRequiresServiceFlow()) return "pay";
        if (!o.isServiceEndedDone()) return "serving";
        if (!o.isFinalCheckDone()) return "check";
        return "pay";
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case "done" -> "已結帳";
            case "wire" -> "待對帳";
            case "serving" -> "服務中";
            case "check" -> "待核對";
            default -> "待結帳";
        };
    }

    // 列表、明細頁用的精簡資料（只放畫面需要的欄位）
    private Map<String, Object> toRow(WalkInOrderResponse o) {
        String title = o.getPetName() != null && !o.getPetName().isBlank() ? o.getPetName()
                : o.getMemberName() != null ? o.getMemberName() : "非會員";
        String stage = stageKey(o);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("title", title);
        m.put("initial", view.initial(title, "客"));
        m.put("sub", (o.getMemberName() != null ? o.getMemberName() : "非會員")
                + "，" + o.getItems().stream().map(WalkInOrderResponse.ItemLine::getItemName)
                        .collect(Collectors.joining("、")));
        m.put("time", o.getCreatedAt() != null ? o.getCreatedAt().format(TIME_FMT) : "");
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().format(DATE_TIME_FMT) : "");
        m.put("amount", o.getChargedAmount() != null ? o.getChargedAmount() : o.getTotalAmount());
        m.put("stage", stage);
        m.put("stageLabel", stageLabel(stage));
        m.put("pendingOperator", o.getItems().stream().anyMatch(i -> !i.isRetailItem() && !i.isOperatorFilled()));
        return m;
    }

    // 搜尋客人：姓名／帳號、電話、毛孩名字，結果依會員分組並帶出名下毛孩
    private List<Map<String, Object>> searchCustomers(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String kw = q.trim();
        Map<String, User> found = new LinkedHashMap<>();
        userService.searchCustomers(kw).forEach(u -> found.putIfAbsent(u.getUsername(), u));

        String digits = kw.replaceAll("[^0-9]", "");
        if (digits.length() >= 3) {
            userRepository.findByRole(UserRole.CUSTOMER).stream()
                    .filter(u -> u.getPhone() != null && u.getPhone().replaceAll("[^0-9]", "").contains(digits))
                    .limit(20)
                    .forEach(u -> found.putIfAbsent(u.getUsername(), u));
        }
        for (Pet p : petRepository.findByNameContainingIgnoreCase(kw)) {
            if (p.getOwner() != null && !p.isDeleted()) {
                found.putIfAbsent(p.getOwner().getUsername(), p.getOwner());
            }
            if (found.size() >= 20) {
                break;
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (User u : found.values()) {
            if (results.size() >= 15) {
                break;
            }
            List<Map<String, Object>> pets = new ArrayList<>();
            try {
                for (PetResponse p : petService.getMyPets(u.getUsername())) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("name", p.getName());
                    pm.put("species", view.speciesKey(p.getPetType() != null ? p.getPetType().name() : null));
                    pm.put("initial", view.initial(p.getName(), "?"));
                    pm.put("meta", p.getBreed() != null ? p.getBreed() : "");
                    pets.add(pm);
                }
            } catch (IllegalArgumentException ignore) {
                // 查不到寵物就只顯示會員本人
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("username", u.getUsername());
            r.put("name", u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName());
            r.put("initial", view.initial(u.getName(), "客"));
            String phone = u.getPhone();
            r.put("phoneTail", phone != null && phone.length() >= 4 ? "末四碼 " + phone.substring(phone.length() - 4) : "");
            r.put("pets", pets);
            results.add(r);
        }
        return results;
    }

    // 建議套餐：只處理狗。已鎖定就是鎖定那項；否則從篩選後的菜單裡抓第一個
    // 有體重級距的項目（跟網頁版 suggestItemCodeForPet() 同一套規則）
    private String suggestItemCode(PetResponse pet, List<GroomingItemResponse> all, List<GroomingItemResponse> menu) {
        if (pet == null || pet.getPetType() == null || !"DOG".equalsIgnoreCase(pet.getPetType().name())) {
            return null;
        }
        if (pet.getLockedGroomingItemId() != null) {
            return all.stream().filter(i -> i.getId().equals(pet.getLockedGroomingItemId()))
                    .map(GroomingItemResponse::getItemCode).findFirst().orElse(null);
        }
        if (pet.getWeight() == null) {
            return null;
        }
        return menu.stream().filter(i -> i.getDogWeightTier() != null)
                .map(GroomingItemResponse::getItemCode).findFirst().orElse(null);
    }

    // 開單失敗時帶回原本的 member / pet / guest 參數，只允許這三個參數，避免被塞入其他內容
    private String safeQuery(String backQuery) {
        if (backQuery == null || backQuery.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String part : backQuery.split("&")) {
            if (part.startsWith("member=") || part.startsWith("pet=") || part.startsWith("guest=")) {
                if (!part.contains("/") && !part.contains(":")) {
                    kept.add(part);
                }
            }
        }
        return kept.isEmpty() ? "" : "?" + String.join("&", kept);
    }
}
