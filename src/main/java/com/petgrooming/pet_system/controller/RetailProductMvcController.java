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
    private final com.petgrooming.pet_system.service.InventoryImportService inventoryImportService; // 需求（2026-10-02）
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

    // 需求（追加）：成本回填專用畫面——只列出還沒設定進貨成本的商品，
    // 一次全部填完，不用從完整商品清單裡自己找哪些還是 0。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/cost-backfill")
    public String costBackfill(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("pendingProducts", retailProductService.listPendingCostBackfill());
        return "admin/retail-product-cost-backfill";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/cost-backfill/submit")
    public String submitCostBackfill(HttpServletRequest request,
                                      @RequestParam java.util.Map<String, String> allParams,
                                      RedirectAttributes ra) {
        User user = getLoginUser(request);
        // 表單欄位命名為 cost_{id}，例如 cost_17=120，逐一解析成 Map<商品id, 成本>
        java.util.Map<Long, Integer> idToCost = new java.util.HashMap<>();
        for (var entry : allParams.entrySet()) {
            if (!entry.getKey().startsWith("cost_")) continue;
            try {
                Long id = Long.parseLong(entry.getKey().substring(5));
                String raw = entry.getValue();
                if (raw == null || raw.isBlank()) continue;
                idToCost.put(id, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // 格式錯誤的欄位直接跳過，不讓單一筆輸入錯誤擋掉其他筆的回填
            }
        }
        int updated = retailProductService.bulkUpdateCost(idToCost);
        operationLogService.log(user, "RETAIL", "BULK_BACKFILL_UNIT_COST",
                "批次回填零售商品成本，共 " + updated + " 筆", null);
        ra.addFlashAttribute("successMsg", "已回填 " + updated + " 筆商品成本");
        return "redirect:/admin/retail-products/cost-backfill";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/create")
    public String create(HttpServletRequest request,
                         @RequestParam String name,
                         @RequestParam int price,
                         @RequestParam(defaultValue = "0") int stockQuantity,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "0") int unitCost,
                         @RequestParam(required = false) String barcode, // 需求（2026-10-02）：留空＝系統產生
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var product = retailProductService.create(name, price, stockQuantity, description, unitCost, barcode);
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
                         @RequestParam(required = false) String barcode,
                         RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            // 表單沒帶條碼欄位＝不改條碼；帶了空白＝改回系統產生的店內條碼
            if (barcode == null) {
                retailProductService.update(id, name, price, description, unitCost);
            } else {
                retailProductService.update(id, name, price, description, unitCost, barcode);
            }
            operationLogService.log(user, "RETAIL", "UPDATE_RETAIL_PRODUCT", "更新商品 #" + id, null);
            ra.addFlashAttribute("successMsg", "已更新商品資訊");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/retail-products";
    }

    // ── 需求（追加，2026-10-02 店家確認 7-6）：盤點（只有管理者）──────────
    @RequireRole({UserRole.ADMIN})
    @PostMapping("/{id}/stocktake")
    public String stocktake(@PathVariable Long id, HttpServletRequest request,
                            @RequestParam int actual, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            int diff = retailProductService.stocktake(id, actual);
            operationLogService.log(user, "RETAIL", "STOCKTAKE_RETAIL",
                    "商品 #" + id + " 盤點為 " + actual + "（差額 " + (diff >= 0 ? "+" : "") + diff + "）", null);
            ra.addFlashAttribute("successMsg", "盤點完成，差額 " + (diff >= 0 ? "+" : "") + diff);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/retail-products";
    }

    // ── 需求（追加，2026-10-02 店家確認 7）：CSV 批次匯入 ──────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/import")
    public String importCsv(HttpServletRequest request,
                            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                            RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            var result = inventoryImportService.importRetail(file);
            operationLogService.log(user, "RETAIL", "IMPORT_RETAIL",
                    "零售商品匯入：成功 " + result.getSucceeded() + " / " + result.getTotalRows(), null);
            ra.addFlashAttribute("importResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "匯入失敗：" + e.getMessage());
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
