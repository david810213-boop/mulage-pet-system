package com.petgrooming.pet_system.export;

import com.petgrooming.pet_system.dto.PerformanceMatrixResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.List;

/**
 * 積分項目統計 Excel：同一個工作表上下兩個區塊——「總計/隻數」與「換算積分」（含每人總分）。
 */
public final class StaffBreakdownExcel {

    private StaffBreakdownExcel() {
    }

    public static XSSFWorkbook build(int year, int month, PerformanceMatrixResponse matrix) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("積分項目統計");
        CellStyle headerStyle = ExcelSupport.headerStyle(workbook);
        List<PerformanceCategory> categories = matrix.getCategories();
        List<String> categoryLabels = categories.stream().map(PerformanceCategory::getLabel).toList();
        String period = year + "年" + month + "月";

        int rowIdx = 0;

        // 區塊一：總計/隻數
        sheet.createRow(rowIdx++).createCell(0).setCellValue(period + "　總計/隻數");
        rowIdx = writeBlockHeader(sheet, rowIdx, headerStyle, categoryLabels);
        for (var r : matrix.getRows()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(r.getStaffName());
            for (int i = 0; i < categories.size(); i++) {
                row.createCell(i + 1).setCellValue(r.getCountByCategory().get(categories.get(i)));
            }
        }
        Row totalsCountRow = sheet.createRow(rowIdx++);
        totalsCountRow.createCell(0).setCellValue("合計");
        for (int i = 0; i < categories.size(); i++) {
            totalsCountRow.createCell(i + 1).setCellValue(matrix.getTotalsRow().getCountByCategory().get(categories.get(i)));
        }

        rowIdx++; // 空一行

        // 區塊二：換算積分
        sheet.createRow(rowIdx++).createCell(0).setCellValue(period + "　換算積分");
        List<String> pointsHeaders = new ArrayList<>(categoryLabels);
        pointsHeaders.add("總分");
        rowIdx = writeBlockHeader(sheet, rowIdx, headerStyle, pointsHeaders);
        for (var r : matrix.getRows()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(r.getStaffName());
            for (int i = 0; i < categories.size(); i++) {
                row.createCell(i + 1).setCellValue(r.getPointsByCategory().get(categories.get(i)));
            }
            row.createCell(categories.size() + 1).setCellValue(r.getTotalPoints());
        }
        Row totalsPointsRow = sheet.createRow(rowIdx++);
        totalsPointsRow.createCell(0).setCellValue("合計");
        for (int i = 0; i < categories.size(); i++) {
            totalsPointsRow.createCell(i + 1).setCellValue(matrix.getTotalsRow().getPointsByCategory().get(categories.get(i)));
        }
        totalsPointsRow.createCell(categories.size() + 1).setCellValue(matrix.getGrandTotal());

        // 第一欄是人名／類別標籤，留寬一點
        sheet.setColumnWidth(0, 14 * 256);
        for (int i = 1; i <= categories.size() + 1; i++) {
            sheet.setColumnWidth(i, 12 * 256);
        }
        return workbook;
    }

    /** 區塊標題列：第一格「人」（不套標題樣式），之後是各欄名稱。 */
    private static int writeBlockHeader(Sheet sheet, int rowIdx, CellStyle headerStyle, List<String> headers) {
        Row header = sheet.createRow(rowIdx);
        header.createCell(0).setCellValue("人");
        ExcelSupport.writeHeaders(header, 1, headerStyle, headers);
        return rowIdx + 1;
    }
}
