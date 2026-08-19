package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CatRewashCandidateResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.service.CatRewashDiscountService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.PrintWriter;
import java.util.List;

/**
 * 需求 8-2：貓咪 90 天回洗優惠名單——篩選＋匯出 Excel/CSV，供店家手動聯繫（不做自動群發）。
 */
@Controller
@RequestMapping("/admin/cat-rewash")
@RequiredArgsConstructor
public class CatRewashMvcController {

    private final CatRewashDiscountService catRewashDiscountService;
    private final PetRepository petRepository;
    private final UserService userService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    // filter: "within"（還在優惠期內）／"overdue"（已超過90天）／不填＝全部有洗澡紀錄的貓
    private List<CatRewashCandidateResponse> loadCandidates(String filter) {
        Boolean withinWindowOnly = switch (filter == null ? "" : filter) {
            case "within" -> true;
            case "overdue" -> false;
            default -> null;
        };
        return catRewashDiscountService.listCandidates(
                petRepository.findByPetType(PetType.CAT), withinWindowOnly);
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(HttpServletRequest request, Model model,
                       @RequestParam(required = false) String filter) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("filter", filter);
        model.addAttribute("candidates", loadCandidates(filter));
        model.addAttribute("rewashWindowDays", CatRewashDiscountService.REWASH_WINDOW_DAYS);
        return "admin/cat-rewash-list";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/export.xlsx")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String filter) throws java.io.IOException {
        List<CatRewashCandidateResponse> candidates = loadCandidates(filter);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("貓咪回洗名單");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"寵物名", "家長姓名", "家長電話", "上次洗澡日期", "距今天數", "是否仍在優惠期內"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (CatRewashCandidateResponse c : candidates) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getPetName());
                row.createCell(1).setCellValue(c.getOwnerName());
                row.createCell(2).setCellValue(c.getOwnerPhone() != null ? c.getOwnerPhone() : "");
                row.createCell(3).setCellValue(c.getLastBathDate().toString());
                row.createCell(4).setCellValue(c.getDaysSinceLastBath());
                row.createCell(5).setCellValue(c.isWithinDiscountWindow() ? "是" : "否（已超過90天）");
            }
            // 需求（追加）：autoSizeColumn() 在 Railway 容器上會因缺少字型函式庫（libfreetype）
            // 直接噴 UnsatisfiedLinkError 導致匯出失敗，改成固定欄寬。
            int[] colWidths = {10, 12, 14, 14, 10, 18};
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, colWidths[i] * 256);
            }

            String filename = "貓咪回洗名單.xlsx";
            String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            workbook.write(response.getOutputStream());
        }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/export.csv")
    public void exportCsv(HttpServletResponse response,
                          @RequestParam(required = false) String filter) throws java.io.IOException {
        List<CatRewashCandidateResponse> candidates = loadCandidates(filter);

        String filename = "貓咪回洗名單.csv";
        String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);

        // UTF-8 BOM，避免 Excel 開啟 CSV 時中文亂碼
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (PrintWriter writer = response.getWriter()) {
            writer.println("寵物名,家長姓名,家長電話,上次洗澡日期,距今天數,是否仍在優惠期內");
            for (CatRewashCandidateResponse c : candidates) {
                writer.printf("%s,%s,%s,%s,%d,%s%n",
                        csvEscape(c.getPetName()),
                        csvEscape(c.getOwnerName()),
                        csvEscape(c.getOwnerPhone()),
                        c.getLastBathDate(),
                        c.getDaysSinceLastBath(),
                        c.isWithinDiscountWindow() ? "是" : "否（已超過90天）");
            }
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
