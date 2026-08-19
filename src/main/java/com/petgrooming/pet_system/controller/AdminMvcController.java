package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.GroomingItemRequest;
import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMvcController {

    private final UserService userService;
    private final PaymentService paymentService;
    private final GroomingService groomingService;

    /**
     * JWT 版獲取當前登入使用者
     * 從 LoginInterceptor 存入的 request attribute 拿取 username，
     * 再用 UserService 查出完整的 User entity
     */
    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        User user = getLoginUser(request);
        return user != null && user.isAdmin();
    }

    /**
     * 1. 首頁：列出使用者、員工，【新增】同時列出所有美容項目與表單對象
     */
    @GetMapping
    public String adminHome(HttpServletRequest request, Model model) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        model.addAttribute("allUsers", userService.getAllUsers());
        model.addAttribute("allStaff", userService.getAllStaff());
        model.addAttribute("createStaffRequest", new CreateStaffRequest());
        model.addAttribute("groomingItems", groomingService.getAllItems());
        //接收服务项目的空物件
        model.addAttribute("groomingItemRequest", new GroomingItemRequest());
        
        return "admin/home";
    }

    // 處理新增員工表單提交
    @PostMapping("/staff/submit")
    public String createStaff(@Valid @ModelAttribute CreateStaffRequest req,
                              BindingResult bindingResult,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                              Model model) {

        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            return "admin/home";
        }

        try {
            userService.createStaff(req);
            redirectAttributes.addFlashAttribute("successMsg", "員工帳號新增成功");
            return "redirect:/admin";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /**
     * 重設員工／管理員密碼（ADMIN 限定，不需驗證舊密碼）
     */
    @PostMapping("/staff/{id}/reset-password")
    public String resetStaffPassword(@PathVariable Long id,
                                     @RequestParam String newPassword,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        if (!isAdmin(request)) return "redirect:/dashboard";

        try {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword(newPassword);
            userService.resetPassword(id, req);
            redirectAttributes.addFlashAttribute("successMsg", "密碼重設成功");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin";
    }

    /**
     * 2. 處理【修改】美容項目表單提交
     */
    @PostMapping("/grooming/update/{id}")
    public String updateItem(@PathVariable Long id,
                             @Valid @ModelAttribute("updateGroomingItemRequest") com.petgrooming.pet_system.dto.UpdateGroomingItemRequest req,
                             BindingResult bindingResult,
                             HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                             Model model) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        // 如果管理員欄位填錯（例如價格填負數）
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", "服務項目修改資料格式錯誤！");
            return "admin/home"; // 留在原地
        }

        try {
            groomingService.updateItem(id, req);
            redirectAttributes.addFlashAttribute("successMsg", "服務項目修改成功！");
            return "redirect:/admin"; // PRG 模式：重導向回管理首頁
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /**
     * 3. 處理【邏輯刪除】美容項目
     * 網頁刪除按鈕會包在一個 POST 表單裡送過來
     */
    @PostMapping("/grooming/delete/{id}")
    public String deleteItem(@PathVariable Long id,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";

        try {
            groomingService.deleteItem(id);
            redirectAttributes.addFlashAttribute("successMsg", "服務項目已成功下架！");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin"; // 刪除完畢後刷新首頁
    }

    // 財務報表頁面
    @GetMapping("/report")
    public String report(HttpServletRequest request, Model model) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);
        model.addAttribute("user", user);
        model.addAttribute("report", paymentService.getFinancialReport());
        return "admin/report";
    }

    // ── GET /admin/report/export.xlsx ───────────────────────────────────
    // 需求 6：財務報表匯出 Excel（當日/當月彙總 + 成本毛利 + 逐筆明細，三個工作表）
    @GetMapping("/report/export.xlsx")
    public void exportReport(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (!isAdmin(request)) {
            response.sendRedirect("/dashboard");
            return;
        }
        var report = paymentService.getFinancialReport();

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // ── 工作表 1：彙總 ──────────────────────────────────────────
            var summarySheet = workbook.createSheet("業績彙總");
            String[][] summaryRows = {
                    {"項目", "數值"},
                    {"當日總業績", String.valueOf(report.getTodayRevenueTotal())},
                    {"當日儲值金扣款", String.valueOf(report.getTodayRevenueWallet())},
                    {"當日非儲值金付款", String.valueOf(report.getTodayRevenueNonWallet())},
                    {"當日結帳筆數", String.valueOf(report.getTodayOrderCount())},
                    {"當日儲值金額（預收款，非業績）", String.valueOf(report.getTodayTopupCollected())},
                    {"當月總業績", String.valueOf(report.getMonthRevenueTotal())},
                    {"當月儲值金扣款", String.valueOf(report.getMonthRevenueWallet())},
                    {"當月非儲值金付款", String.valueOf(report.getMonthRevenueNonWallet())},
                    {"當月結帳筆數", String.valueOf(report.getMonthOrderCount())},
                    {"當月儲值金額（預收款，非業績）", String.valueOf(report.getMonthTopupCollected())},
                    {"零售商品成本（估算）", String.valueOf(report.getMonthRetailCostEstimate())},
                    {"店用洗劑成本", String.valueOf(report.getMonthSupplyCost())},
                    {"成本合計", String.valueOf(report.getMonthTotalCost())},
                    {"粗估毛利", String.valueOf(report.getMonthEstimatedProfit())},
            };
            for (int r = 0; r < summaryRows.length; r++) {
                var row = summarySheet.createRow(r);
                for (int c = 0; c < summaryRows[r].length; c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(summaryRows[r][c]);
                    if (r == 0) cell.setCellStyle(headerStyle);
                }
            }
            // 需求（追加）：autoSizeColumn() 底層要呼叫 Java AWT 字型系統量測文字寬度，
            // Railway 的容器沒有安裝 libfreetype 等字型函式庫，一呼叫就會直接噴
            // UnsatisfiedLinkError 導致整個匯出失敗。改成固定欄寬，不依賴字型系統。
            summarySheet.setColumnWidth(0, 32 * 256);
            summarySheet.setColumnWidth(1, 14 * 256);

            // ── 工作表 2：交易明細 ──────────────────────────────────────
            var detailSheet = workbook.createSheet("交易明細");
            String[] detailHeaders = {"來源", "單號", "付款方式", "金額", "付款時間", "經手人"};
            var detailHeaderRow = detailSheet.createRow(0);
            for (int i = 0; i < detailHeaders.length; i++) {
                var cell = detailHeaderRow.createCell(i);
                cell.setCellValue(detailHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (var d : report.getDetails()) {
                var row = detailSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(d.getSourceLabel());
                row.createCell(1).setCellValue(d.getCode());
                row.createCell(2).setCellValue(d.getPaymentMethodLabel());
                row.createCell(3).setCellValue(d.getAmount());
                row.createCell(4).setCellValue(d.getPaymentTime() != null
                        ? d.getPaymentTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
                row.createCell(5).setCellValue(d.getHandledBy() != null ? d.getHandledBy() : "");
            }
            int[] detailColWidths = {10, 12, 12, 10, 20, 16};
            for (int i = 0; i < detailHeaders.length; i++) {
                detailSheet.setColumnWidth(i, detailColWidths[i] * 256);
            }

            String filename = "財務報表_" + java.time.LocalDate.now() + ".xlsx";
            String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            workbook.write(response.getOutputStream());
        }
    }

        /**
     * 4. 處理【新增】美容服務項目表單提交
     */
    @PostMapping("/grooming/submit")
    public String createGroomingItem(@Valid @ModelAttribute("groomingItemRequest") GroomingItemRequest req,
                                    BindingResult bindingResult,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";
        User user = getLoginUser(request);

        // 欄位校正，萬一管理員沒填名稱或價格寫負數
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", "新增失敗：請檢查輸入欄位是否正確！");
            return "admin/home"; // 留在原地改考卷
        }

        try {
            groomingService.createItem(req);
            redirectAttributes.addFlashAttribute("successMsg", "✨ 成功建立新的美容項目！");
            return "redirect:/admin";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("allUsers", userService.getAllUsers());
            model.addAttribute("allStaff", userService.getAllStaff());
            model.addAttribute("groomingItems", groomingService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }
}