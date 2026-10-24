package com.petgrooming.pet_system.export;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel／CSV 匯出共用的小工具：標題列樣式、固定欄寬、下載回應標頭。
 */
public final class ExcelSupport {

    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelSupport() {
    }

    /** 標題列樣式：粗體、淺灰底。 */
    public static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /** 從 startColumn 起依序寫入套用標題樣式的文字。 */
    public static void writeHeaders(Row row, int startColumn, CellStyle style, List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(startColumn + i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    /**
     * 固定欄寬（單位：字元數，從第 0 欄開始）。
     * 不用 autoSizeColumn()：它底層要呼叫 Java AWT 字型系統量測文字寬度，Railway 的容器
     * 沒有安裝 libfreetype 等字型函式庫，一呼叫就會噴 UnsatisfiedLinkError 導致整個匯出失敗。
     */
    public static void setColumnWidths(Sheet sheet, int... widthsInChars) {
        for (int i = 0; i < widthsInChars.length; i++) {
            sheet.setColumnWidth(i, widthsInChars[i] * 256);
        }
    }

    /** 下載用的 Content-Disposition 標頭（中文檔名以 RFC 5987 編碼）。 */
    public static void setAttachment(HttpServletResponse response, String contentType, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /** 把活頁簿當成 .xlsx 附件寫進回應。 */
    public static void writeXlsx(HttpServletResponse response, String filename, Workbook workbook) throws IOException {
        setAttachment(response, XLSX_CONTENT_TYPE, filename);
        workbook.write(response.getOutputStream());
    }
}
