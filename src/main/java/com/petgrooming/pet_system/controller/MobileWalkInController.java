package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.MobileWalkInViews;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 員工手機版：現場開單。
 *
 * 流程跟網頁版現場開單完全相同：開單 → 結束服務 → 核對（家長簽名）→ 結帳；
 * 純零售單（沒有美容服務項目）開單後直接結帳。後端呼叫跟網頁版相同的
 * 現場單 service，沒有另外實作任何業務規則。
 *
 * 跟網頁版的差異（手機版操作調整）：
 * - 選客人改成「搜尋 → 點毛孩」兩步，菜單由伺服器端依毛孩體型篩好再送出
 * - 經手人跟網頁版一樣逐項選（店裡一張單常由兩三人分攤），每項預設自己，
 *   另外提供「全部設為」一鍵套用；也可以選「稍後補填」，開單後在明細補
 */
@Controller
@RequestMapping("/m/walk-in")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileWalkInController {


    private final WalkInOrderService walkInOrderService;
    private final UserService userService;
    private final PetService petService;
    private final GroomingService groomingItemService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final RetailProductService retailProductService;
    private final OperationLogService operationLogService;
    private final MobileViewHelper view;
    private final MobileWalkInViews walkInViews; // 列表列、流程階段、搜尋客人、建議套餐


    // ── 現場單列表：未結帳全部＋今天已結帳 ─────────────────────────────────
    @GetMapping({ "", "/" })
    public String list(Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        LocalDate today = LocalDate.now();
        List<WalkInOrderResponse> all = walkInOrderService.listAll();
        List<Map<String, Object>> open = all.stream().filter(o -> !o.isPaid()).map(walkInViews::toRow).toList();
        List<Map<String, Object>> doneToday = all.stream()
                .filter(o -> o.isPaid() && o.getPaymentTime() != null && o.getPaymentTime().toLocalDate().isEqual(today))
                .map(walkInViews::toRow).toList();

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
    public String newOrder(Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) String pet,
            @RequestParam(required = false) String guest,
            @CurrentUser User user) {
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
            model.addAttribute("results", walkInViews.searchCustomers(q));
            return "m/walk-in-new";
        }

        String memberName = null;
        PetResponse selectedPet = null;
        if (hasMember) {
            User m = userService.findByUsername(member).orElse(null);
            if (m == null) {
                return "redirect:/m/walk-in/new";
            }
            memberName = m.getName();
            selectedPet = findPet(member, pet);
        }

        List<GroomingItemResponse> all = groomingItemService.getAllItems();
        // 跟網頁版開單頁一致：有選到毛孩才依物種＋體型篩選；沒選毛孩（非會員、
        // 或會員沒指定毛孩）顯示全部項目，讓店員自己判斷
        List<GroomingItemResponse> menu = selectedPet != null
                ? groomingMenuFilter.filterForPetShape(all, true, selectedPet.getPetType().name(), selectedPet)
                : all;
        model.addAttribute("weightMissing", selectedPet != null
                && groomingMenuFilter.needsWeight(selectedPet.getPetType().name(), selectedPet));

        model.addAttribute("step", "form");
        model.addAttribute("guest", isGuest);
        model.addAttribute("backQuery", backQuery(isGuest, member, pet));
        model.addAttribute("memberUsername", hasMember ? member : "");
        model.addAttribute("memberName", memberName);
        model.addAttribute("pet", selectedPet);
        model.addAttribute("petSpecies", selectedPet != null ? view.speciesKey(selectedPet.getPetType().name()) : "other");
        model.addAttribute("petInitial", selectedPet != null ? view.initial(selectedPet.getName(), "?")
                : (hasMember ? view.initial(memberName, "?") : "客"));
        model.addAttribute("mainItems", menu.stream().filter(i -> !"OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("otherItems", menu.stream().filter(i -> "OTHER".equals(i.getPerformanceCategory())).toList());
        model.addAttribute("suggestedCode", walkInViews.suggestItemCode(selectedPet, all, menu));
        model.addAttribute("staffOptions", view.staffOptions());
        model.addAttribute("meId", user.getId());
        model.addAttribute("retailProducts", retailProductService.listActive().stream()
                .filter(p -> p.getStockQuantity() > 0).toList());
        return "m/walk-in-new";
    }

    @PostMapping("/create")
    public String create(RedirectAttributes ra,
            @RequestParam(required = false) String memberUsername,
            @RequestParam(required = false) String petName,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> itemCodes,
            @RequestParam(required = false) List<String> operatorStaffIds,
            @RequestParam(required = false) List<String> operatorStaffIds2, // 第二經手人
            @RequestParam(required = false) List<String> retailProductIds,
            @RequestParam(required = false) List<String> retailQuantities,
            @RequestParam(required = false) String backQuery,
            @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(memberUsername != null && !memberUsername.isBlank() ? memberUsername : null);
        req.setPetName(petName != null && !petName.isBlank() ? petName.trim() : null);
        req.setNote(note);

        List<WalkInOrderCreateRequest.Item> items =
                WalkInOrderForm.serviceItems(itemCodes, operatorStaffIds, operatorStaffIds2);
        req.setItems(items);
        List<WalkInOrderCreateRequest.RetailItem> retailItems = retailItems(retailProductIds, retailQuantities);
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

    /** 會員指定的毛孩（依名字比對）；沒指定或找不到時是 null。 */
    private PetResponse findPet(String member, String petName) {
        if (petName == null || petName.isBlank()) return null;
        return petService.getMyPets(member).stream()
                .filter(p -> petName.equals(p.getName()))
                .findFirst().orElse(null);
    }

    /** 開單失敗時要帶回同一個客人／毛孩的網址參數。 */
    private static String backQuery(boolean isGuest, String member, String pet) {
        if (isGuest) return "guest=1";
        StringBuilder bq = new StringBuilder("member=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        if (pet != null && !pet.isBlank()) {
            bq.append("&pet=").append(URLEncoder.encode(pet, StandardCharsets.UTF_8));
        }
        return bq.toString();
    }

    /** 手機版每個商品都有數量欄位：數量 0、空白或不是數字都代表沒買，跳過。 */
    private static List<WalkInOrderCreateRequest.RetailItem> retailItems(List<String> retailProductIds,
                                                                         List<String> retailQuantities) {
        List<WalkInOrderCreateRequest.RetailItem> retailItems = new ArrayList<>();
        if (retailProductIds == null) return retailItems;
        for (int i = 0; i < retailProductIds.size(); i++) {
            String qtyStr = retailQuantities != null && i < retailQuantities.size() ? retailQuantities.get(i) : "0";
            int qty;
            try {
                qty = qtyStr == null || qtyStr.isBlank() ? 0 : Integer.parseInt(qtyStr.trim());
            } catch (NumberFormatException e) {
                qty = 0;
            }
            if (qty <= 0) continue;
            WalkInOrderCreateRequest.RetailItem r = new WalkInOrderCreateRequest.RetailItem();
            r.setRetailProductId(Long.valueOf(retailProductIds.get(i)));
            r.setQuantity(qty);
            retailItems.add(r);
        }
        return retailItems;
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
