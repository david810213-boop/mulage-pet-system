package com.petgrooming.pet_system.page;

import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import com.petgrooming.pet_system.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excel 匯出（財務報表、積分項目統計、貓咪回洗名單）：檔名、工作表、標題列與欄寬。
 */
class ExcelExportTest extends IntegrationTestBase {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;
    @Autowired private PaymentService paymentService;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void financialReportHasSummaryDiscountAndDetailSheets() throws Exception {
        User admin = testData.admin();
        MockHttpServletResponse response = download(admin, "/admin/report/export.xlsx");
        assertAttachment(response, "財務報表_" + LocalDate.now() + ".xlsx");
        var report = transactionTemplate.execute(s -> paymentService.getFinancialReport());

        try (Workbook workbook = open(response)) {
            Sheet summary = workbook.getSheet("業績彙總");
            assertThat(row(summary, 0)).containsExactly("項目", "數值");
            assertThat(bold(workbook, summary.getRow(0).getCell(1))).isTrue();
            assertThat(bold(workbook, summary.getRow(1).getCell(0))).isFalse();
            assertThat(row(summary, 1)).containsExactly("當日總業績", String.valueOf(report.getTodayRevenueTotal()));
            assertThat(row(summary, 16)).containsExactly("粗估毛利", String.valueOf(report.getMonthEstimatedProfit()));
            assertThat(summary.getRow(17).getCell(0).getStringCellValue()).startsWith("當日折扣");
            assertThat(summary.getColumnWidth(0)).isEqualTo(32 * 256);
            assertThat(summary.getColumnWidth(1)).isEqualTo(14 * 256);

            Sheet detail = workbook.getSheet("交易明細");
            assertThat(row(detail, 0)).containsExactly("來源", "單號", "付款方式", "金額", "付款時間", "經手人");
            assertThat(detail.getLastRowNum()).isEqualTo(report.getDetails().size());
            assertThat(detail.getColumnWidth(4)).isEqualTo(20 * 256);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("業績彙總");
        }
    }

    @Test
    void financialReportIsAdminOnly() throws Exception {
        User staff = testData.staff();
        mvc.perform(get("/admin/report/export.xlsx").cookie(cookie(staff)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void staffBreakdownHasCountAndPointsBlocks() throws Exception {
        LocalDate today = LocalDate.now();
        MockHttpServletResponse response = download(testData.admin(),
                "/admin/performance/staff-breakdown/export?year=" + today.getYear() + "&month=" + today.getMonthValue());
        assertAttachment(response, today.getYear() + "年" + today.getMonthValue() + "月積分項目統計.xlsx");

        try (Workbook workbook = open(response)) {
            Sheet sheet = workbook.getSheet("積分項目統計");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(today.getYear() + "年" + today.getMonthValue() + "月　總計/隻數");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("人");
            List<String> header = row(sheet, 1);
            int categories = header.size() - 1;
            assertThat(bold(workbook, sheet.getRow(1).getCell(0))).isFalse(); // 「人」這格原本就沒套標題樣式
            if (categories > 0) assertThat(bold(workbook, sheet.getRow(1).getCell(1))).isTrue();

            int countTotalsRow = findRow(sheet, "合計", 2);
            int pointsTitleRow = countTotalsRow + 2;
            assertThat(sheet.getRow(pointsTitleRow).getCell(0).getStringCellValue())
                    .isEqualTo(today.getYear() + "年" + today.getMonthValue() + "月　換算積分");
            List<String> pointsHeader = row(sheet, pointsTitleRow + 1);
            assertThat(pointsHeader).hasSize(categories + 2).endsWith("總分");
            assertThat(pointsHeader.subList(1, categories + 1)).isEqualTo(header.subList(1, categories + 1));
            int pointsTotalsRow = findRow(sheet, "合計", pointsTitleRow + 2);
            assertThat(sheet.getRow(pointsTotalsRow).getLastCellNum()).isEqualTo((short) (categories + 2));
            assertThat(sheet.getColumnWidth(0)).isEqualTo(14 * 256);
            assertThat(sheet.getColumnWidth(categories + 1)).isEqualTo(12 * 256);
        }
    }

    @Test
    void catRewashListHasHeaders() throws Exception {
        MockHttpServletResponse response = download(testData.staff(), "/admin/cat-rewash/export.xlsx");
        assertAttachment(response, "貓咪回洗名單.xlsx");

        try (Workbook workbook = open(response)) {
            Sheet sheet = workbook.getSheet("貓咪回洗名單");
            assertThat(row(sheet, 0)).containsExactly("寵物名", "家長姓名", "家長電話", "上次洗澡日期", "距今天數", "是否仍在優惠期內");
            assertThat(sheet.getColumnWidth(5)).isEqualTo(18 * 256);
        }
    }

    private MockHttpServletResponse download(User user, String url) throws Exception {
        return mvc.perform(get(url).cookie(cookie(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static void assertAttachment(MockHttpServletResponse response, String filename) {
        assertThat(response.getContentType()).isEqualTo(XLSX);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private Cookie cookie(User user) {
        return new Cookie("JWT_TOKEN", jwtUtils.generateToken(user.getUsername(), user.getRole().name()));
    }

    private static Workbook open(MockHttpServletResponse response) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
    }

    private static boolean bold(Workbook workbook, Cell cell) {
        return workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold();
    }

    private static int findRow(Sheet sheet, String firstCell, int from) {
        for (int r = from; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(0) != null && firstCell.equals(row.getCell(0).getStringCellValue())) return r;
        }
        throw new AssertionError("找不到「" + firstCell + "」列");
    }

    private static List<String> row(Sheet sheet, int index) {
        List<String> values = new ArrayList<>();
        for (Cell cell : sheet.getRow(index)) {
            values.add(switch (cell.getCellType()) {
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                default -> cell.getStringCellValue();
            });
        }
        return values;
    }
}
