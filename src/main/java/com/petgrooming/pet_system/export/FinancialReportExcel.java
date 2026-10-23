package com.petgrooming.pet_system.export;

import com.petgrooming.pet_system.dto.FinancialReportResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 財務報表 Excel：「業績彙總」（含當日／當月折扣統計）與「交易明細」兩個工作表。
 */
public final class FinancialReportExcel {

    private static final DateTimeFormatter PAYMENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private FinancialReportExcel() {
    }

    public static XSSFWorkbook build(FinancialReportResponse report) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = ExcelSupport.headerStyle(workbook);
        writeSummary(workbook.createSheet("業績彙總"), headerStyle, report);
        writeDetails(workbook.createSheet("交易明細"), headerStyle, report);
        return workbook;
    }

    private static void writeSummary(Sheet sheet, CellStyle headerStyle, FinancialReportResponse report) {
        ExcelSupport.writeHeaders(sheet.createRow(0), 0, headerStyle, List.of("項目", "數值"));
        Object[][] rows = {
                {"當日總業績", report.getTodayRevenueTotal()},
                {"當日儲值金扣款", report.getTodayRevenueWallet()},
                {"當日非儲值金付款", report.getTodayRevenueNonWallet()},
                {"當日結帳筆數", report.getTodayOrderCount()},
                {"當日零售商品營收（已含在總業績內）", report.getTodayRetailRevenue()},
                {"當日儲值金額（預收款，非業績）", report.getTodayTopupCollected()},
                {"當月總業績", report.getMonthRevenueTotal()},
                {"當月儲值金扣款", report.getMonthRevenueWallet()},
                {"當月非儲值金付款", report.getMonthRevenueNonWallet()},
                {"當月結帳筆數", report.getMonthOrderCount()},
                {"當月零售商品營收（已含在總業績內）", report.getMonthRetailRevenue()},
                {"當月儲值金額（預收款，非業績）", report.getMonthTopupCollected()},
                {"零售商品成本（估算）", report.getMonthRetailCostEstimate()},
                {"店用洗劑成本", report.getMonthSupplyCost()},
                {"成本合計", report.getMonthTotalCost()},
                {"粗估毛利", report.getMonthEstimatedProfit()},
        };
        int rowIndex = 1;
        for (Object[] values : rows) {
            var row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue((String) values[0]);
            row.createCell(1).setCellValue(String.valueOf(values[1])); // 沿用原本的格式：數值以文字寫入
        }
        rowIndex = writeDiscountRows(sheet, rowIndex, "當日折扣", report.getTodayDiscounts());
        writeDiscountRows(sheet, rowIndex, "當月折扣", report.getMonthDiscounts());
        ExcelSupport.setColumnWidths(sheet, 32, 14);
    }

    /** 折扣統計列，例如「當日折扣 9折｜3 筆（折讓 450）」；沒有折扣時寫一列「沒有折扣訂單」。 */
    private static int writeDiscountRows(Sheet sheet, int rowIndex, String period,
                                         List<FinancialReportResponse.DiscountSummary> discounts) {
        if (discounts.isEmpty()) {
            var row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(period);
            row.createCell(1).setCellValue("沒有折扣訂單");
            return rowIndex;
        }
        for (var d : discounts) {
            var row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(period + " " + d.getLabel());
            row.createCell(1).setCellValue(d.getOrderCount() + " 筆（折讓 " + d.getDiscountAmount() + "）");
        }
        return rowIndex;
    }

    private static void writeDetails(Sheet sheet, CellStyle headerStyle, FinancialReportResponse report) {
        ExcelSupport.writeHeaders(sheet.createRow(0), 0, headerStyle,
                List.of("來源", "單號", "付款方式", "金額", "付款時間", "經手人"));
        int rowIndex = 1;
        for (var d : report.getDetails()) {
            var row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(d.getSourceLabel());
            row.createCell(1).setCellValue(d.getCode());
            row.createCell(2).setCellValue(d.getPaymentMethodLabel());
            row.createCell(3).setCellValue(d.getAmount());
            row.createCell(4).setCellValue(d.getPaymentTime() != null ? d.getPaymentTime().format(PAYMENT_TIME) : "");
            row.createCell(5).setCellValue(d.getHandledBy() != null ? d.getHandledBy() : "");
        }
        ExcelSupport.setColumnWidths(sheet, 10, 12, 12, 10, 20, 16);
    }
}
