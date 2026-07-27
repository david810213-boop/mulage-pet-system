package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
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
                               RedirectAttributes ra) {
        try {
            walkInOrderService.fillOperator(itemId, staffId);
            ra.addFlashAttribute("successMsg", "已補填經手人");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "補填失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
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
            ra.addFlashAttribute("successMsg",
                    "結帳完成！單號 #" + result.getId() + "，付款方式：" + result.getPaymentMethodLabel());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "結帳失敗：" + e.getMessage());
        }
        return "redirect:/admin/walk-in-orders";
    }
}
