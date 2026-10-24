package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.InventoryImportService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.StoreSupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 店用洗劑後台管理——新增品項、進貨補貨、員工領用登記、下架。
 */
@Controller
@RequestMapping("/admin/store-supplies")
@RequiredArgsConstructor
public class StoreSupplyMvcController {

    private final StoreSupplyService storeSupplyService;
    private final InventoryImportService inventoryImportService;
    private final OperationLogService operationLogService;

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("supplies", storeSupplyService.listActive());
        return "admin/store-supplies";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/create")
    public String create(@RequestParam String name,
                         @RequestParam(defaultValue = "0") int stockQuantity,
                         @RequestParam(defaultValue = "0") int safetyStockThreshold,
                         @RequestParam(defaultValue = "0") int unitCost,
                         @RequestParam(required = false) String barcode, // 留空＝系統產生
                         RedirectAttributes ra,
                         @CurrentUser User user) {
        try {
            var supply = storeSupplyService.create(name, stockQuantity, safetyStockThreshold, unitCost, barcode);
            operationLogService.log(user, "SUPPLY", "CREATE_SUPPLY", "新增店用洗劑：" + supply.getName(), null);
            ra.addFlashAttribute("successMsg", "已新增「" + supply.getName() + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam int safetyStockThreshold,
                         @RequestParam int unitCost,
                         @RequestParam(required = false) String barcode,
                         RedirectAttributes ra,
                         @CurrentUser User user) {
        try {
            // 表單沒帶條碼欄位＝不改條碼；帶了空白＝改回系統產生的店內條碼
            if (barcode == null) {
                storeSupplyService.update(id, name, safetyStockThreshold, unitCost);
            } else {
                storeSupplyService.update(id, name, safetyStockThreshold, unitCost, barcode);
            }
            operationLogService.log(user, "SUPPLY", "UPDATE_SUPPLY", "更新店用洗劑 #" + id, null);
            ra.addFlashAttribute("successMsg", "已更新資訊");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    // ── 盤點（只有管理者）──────────
    @RequireRole({UserRole.ADMIN})
    @PostMapping("/{id}/stocktake")
    public String stocktake(@PathVariable Long id,
                            @RequestParam int actual, RedirectAttributes ra,
                            @CurrentUser User user) {
        try {
            int diff = storeSupplyService.stocktake(id, actual);
            operationLogService.log(user, "SUPPLY", "STOCKTAKE_SUPPLY",
                    "店用洗劑 #" + id + " 盤點為 " + actual + "（差額 " + (diff >= 0 ? "+" : "") + diff + "）", null);
            ra.addFlashAttribute("successMsg", "盤點完成，差額 " + (diff >= 0 ? "+" : "") + diff);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    // ── CSV 批次匯入 ──────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            RedirectAttributes ra,
                            @CurrentUser User user) {
        try {
            var result = inventoryImportService.importSupplies(file);
            operationLogService.log(user, "SUPPLY", "IMPORT_SUPPLY",
                    "店用洗劑匯入：成功 " + result.getSucceeded() + " / " + result.getTotalRows(), null);
            ra.addFlashAttribute("importResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "匯入失敗：" + e.getMessage());
        }
        return "redirect:/admin/store-supplies";
    }

    // 進貨補貨
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/restock")
    public String restock(@PathVariable Long id,
                          @RequestParam int quantity,
                          RedirectAttributes ra,
                          @CurrentUser User user) {
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

    // 員工領用登記
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/use")
    public String use(@PathVariable Long id,
                      @RequestParam int quantity,
                      @RequestParam(required = false) String note,
                      RedirectAttributes ra,
                      @CurrentUser User user) {
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
    public String delete(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        storeSupplyService.softDelete(id);
        operationLogService.log(user, "SUPPLY", "DELETE_SUPPLY", "下架店用洗劑 #" + id, null);
        ra.addFlashAttribute("successMsg", "已下架");
        return "redirect:/admin/store-supplies";
    }
}
