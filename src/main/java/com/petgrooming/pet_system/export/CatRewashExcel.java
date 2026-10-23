package com.petgrooming.pet_system.export;

import com.petgrooming.pet_system.dto.CatRewashCandidateResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

/**
 * 貓咪回洗名單 Excel（需求 8-2）。
 */
public final class CatRewashExcel {

    public static final List<String> HEADERS =
            List.of("寵物名", "家長姓名", "家長電話", "上次洗澡日期", "距今天數", "是否仍在優惠期內");

    private CatRewashExcel() {
    }

    public static XSSFWorkbook build(List<CatRewashCandidateResponse> candidates) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("貓咪回洗名單");
        ExcelSupport.writeHeaders(sheet.createRow(0), 0, ExcelSupport.headerStyle(workbook), HEADERS);

        int rowIdx = 1;
        for (CatRewashCandidateResponse c : candidates) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(c.getPetName());
            row.createCell(1).setCellValue(c.getOwnerName());
            row.createCell(2).setCellValue(c.getOwnerPhone() != null ? c.getOwnerPhone() : "");
            row.createCell(3).setCellValue(c.getLastBathDate().toString());
            row.createCell(4).setCellValue(c.getDaysSinceLastBath());
            row.createCell(5).setCellValue(withinWindowLabel(c));
        }
        ExcelSupport.setColumnWidths(sheet, 10, 12, 14, 14, 10, 18);
        return workbook;
    }

    public static String withinWindowLabel(CatRewashCandidateResponse c) {
        return c.isWithinDiscountWindow() ? "是" : "否（已超過90天）";
    }
}
