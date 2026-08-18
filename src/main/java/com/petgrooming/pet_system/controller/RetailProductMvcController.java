package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 需求 7-1：零售商品後台管理——新增商品、調整售價、補貨/盤點調整庫存、下架。
 */
@Controller
@RequestMapping("/admin/retail-products")
@RequiredArgsConstructor
public class RetailProductMvcController {

    private final RetailProductService retailProductService;
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
        model.addAttribute("products", retailProductService.listActive());
        return "admin/retail-products";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/create")
    public String create(HttpServletRequest request,
                         @RequestParam String name,
                         @RequestParam int price,
                         @RequestParam(defaultValue = "0") int stockQuantity,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "0") int unitCost,
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var product = retailProductService.create(name, price, stockQuantity, description, unitCost);
            operationLogService.log(user, "RETAIL", "CREATE_RETAIL_PRODUCT",
                    "新增商品：" + product.getName(), null);
            ra.addFlashAttribute("successMsg", "已新增商品「" + product.getName() + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/retail-products";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, HttpServletRequest request,
                         @RequestParam String name,
                         @RequestParam int price,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "0") int unitCost,
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            retailProductService.update(id, name, price, description, unitCost);
            operationLogService.log(user, "RETAIL", "UPDATE_RETAIL_PRODUCT", "更新商品 #" + id, null);
            ra.addFlashAttribute("successMsg", "已更新商品資訊");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/retail-products";
    }

    // 補貨／盤點修正：delta 正數＝入庫，負數＝扣減（例如報損）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/adjust-stock")
    public String adjustStock(@PathVariable Long id, HttpServletRequest request,
                              @RequestParam int delta,
                              RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            retailProductService.adjustStock(id, delta);
            operationLogService.log(user, "RETAIL", "ADJUST_RETAIL_STOCK",
                    "商品 #" + id + " 庫存調整 " + (delta >= 0 ? "+" : "") + delta, null);
            ra.addFlashAttribute("successMsg", "庫存已調整");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/retail-products";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        retailProductService.softDelete(id);
        operationLogService.log(user, "RETAIL", "DELETE_RETAIL_PRODUCT", "下架商品 #" + id, null);
        ra.addFlashAttribute("successMsg", "商品已下架");
        return "redirect:/admin/retail-products";
    }
}
