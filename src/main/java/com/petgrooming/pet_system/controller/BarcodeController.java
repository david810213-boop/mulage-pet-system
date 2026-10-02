package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.BarcodeService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.StoreSupplyService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求（追加，2026-10-02 店家確認 7）：條碼查詢、補產生條碼、列印條碼標籤。
 * 掃碼槍等同鍵盤輸入，各頁面的掃碼框（/js/barcode-scan.js）都打 lookup API 查商品。
 */
@Controller
@RequiredArgsConstructor
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final RetailProductService retailProductService;
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

    // GET /api/admin/inventory/barcode?code=xxx
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/api/admin/inventory/barcode")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookup(@RequestParam String code) {
        try {
            return ResponseEntity.ok(barcodeService.lookup(code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("found", false, "message", e.getMessage()));
        }
    }

    // POST /admin/barcodes/generate-missing：幫還沒有條碼的上架商品產生店內條碼
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/admin/barcodes/generate-missing")
    public String generateMissing(HttpServletRequest request,
                                  @RequestParam(defaultValue = "/admin/retail-products") String back,
                                  RedirectAttributes ra) {
        int n = barcodeService.generateMissing();
        operationLogService.log(getLoginUser(request), "RETAIL", "GENERATE_BARCODES", "補產生條碼 " + n + " 筆", null);
        ra.addFlashAttribute("successMsg", n > 0 ? "已產生 " + n + " 個店內條碼" : "所有商品都已經有條碼了");
        return "redirect:" + (back.startsWith("/admin/") ? back : "/admin/retail-products");
    }

    // GET /admin/barcodes/print?type=retail|supply|all：列印條碼標籤
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/admin/barcodes/print")
    public String print(@RequestParam(defaultValue = "all") String type,
                        @RequestParam(defaultValue = "false") boolean onlyStore,
                        HttpServletRequest request, Model model) {
        List<Map<String, Object>> labels = new ArrayList<>();
        if (!"supply".equals(type)) {
            retailProductService.listActive().forEach(p -> addLabel(labels, p.getName(), p.getBarcode(),
                    "$" + p.getPrice(), onlyStore));
        }
        if (!"retail".equals(type)) {
            storeSupplyService.listActive().forEach(s -> addLabel(labels, s.getName(), s.getBarcode(),
                    "店用", onlyStore));
        }
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("labels", labels);
        model.addAttribute("type", type);
        model.addAttribute("onlyStore", onlyStore);
        return "admin/barcode-labels";
    }

    // onlyStore＝只印系統產生的店內條碼（20 開頭，原廠商品包裝上本來就有條碼不用再印）
    private static void addLabel(List<Map<String, Object>> labels, String name, String code, String sub,
                                 boolean onlyStore) {
        if (code == null || code.isBlank()) return;
        if (onlyStore && !code.startsWith("20")) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("code", code);
        m.put("sub", sub);
        m.put("ean13", code.matches("\\d{13}"));
        labels.add(m);
    }
}
