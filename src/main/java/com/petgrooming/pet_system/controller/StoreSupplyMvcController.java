package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.StoreSupplyService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 需求 7-2：店用洗劑後台管理——新增品項、進貨補貨、員工領用登記、下架。
 */
@Controller
@RequestMapping("/admin/store-supplies")
@RequiredArgsConstructor
public class StoreSupplyMvcController {

    private final StoreSupplyService storeSupplyService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("supplies", storeSupplyService.listActive());
        return "admin/store-supplies";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/create")
    public String create(HttpServletRequest request,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "0") int stockQuantity,
                         @RequestParam(defaultValue = "0") int safetyStockThreshold,
                         @RequestParam(defaultValue = "0") int unitCost,
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var supply = storeSupplyService.create(name, stockQuantity, safetyStockThreshold, unitCost);
            operationLogService.log(user, "SUPPLY", "CREATE_SUPPLY", "新增店用洗劑：" + supply.getName(), null);
            ra.addFlashAttribute("successMsg", "已新增「" + supply.getName() + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, HttpServletRequest request,
                         @RequestParam String name,
                         @RequestParam int safetyStockThreshold,
                         @RequestParam int unitCost,
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            storeSupplyService.update(id, name, safetyStockThreshold, unitCost);
            operationLogService.log(user, "SUPPLY", "UPDATE_SUPPLY", "更新店用洗劑 #" + id, null);
            ra.addFlashAttribute("successMsg", "已更新資訊");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    // 進貨補貨
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/restock")
    public String restock(@PathVariable Long id, HttpServletRequest request,
                          @RequestParam int quantity,
                          RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            storeSupplyService.restock(id, quantity);
            operationLogService.log(user, "SUPPLY", "RESTOCK_SUPPLY",
                    "店用洗劑 #" + id + " 進貨 +" + quantity, null);
            ra.addFlashAttribute("successMsg", "已登記進貨");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    // 需求 7-2：員工領用登記
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/use")
    public String use(@PathVariable Long id, HttpServletRequest request,
                      @RequestParam int quantity,
                      @RequestParam(required = false) String note,
                      RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            storeSupplyService.recordUsage(id, quantity, user.getUsername(), note);
            operationLogService.log(user, "SUPPLY", "USE_SUPPLY",
                    "店用洗劑 #" + id + " 領用 -" + quantity, note);
            ra.addFlashAttribute("successMsg", "已登記領用");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        storeSupplyService.softDelete(id);
        operationLogService.log(user, "SUPPLY", "DELETE_SUPPLY", "下架店用洗劑 #" + id, null);
        ra.addFlashAttribute("successMsg", "已下架");
        return "redirect:/admin/store-supplies";
    }
}
