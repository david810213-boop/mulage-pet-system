package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CatRewashCandidateResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.export.CatRewashExcel;
import com.petgrooming.pet_system.export.ExcelSupport;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.CatRewashDiscountService;
import com.petgrooming.pet_system.service.PetService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
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
    private final PetService petService;

    // filter: "within"（還在優惠期內）／"overdue"（已超過90天）／不填＝全部有洗澡紀錄的貓
    private List<CatRewashCandidateResponse> loadCandidates(String filter) {
        Boolean withinWindowOnly = switch (filter == null ? "" : filter) {
            case "within" -> true;
            case "overdue" -> false;
            default -> null;
        };
        return catRewashDiscountService.listCandidates(
                petService.findAllByType(PetType.CAT), withinWindowOnly);
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String filter,
                       @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("filter", filter);
        model.addAttribute("candidates", loadCandidates(filter));
        model.addAttribute("rewashWindowDays", CatRewashDiscountService.REWASH_WINDOW_DAYS);
        return "admin/cat-rewash-list";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/export.xlsx")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String filter) throws IOException {
        List<CatRewashCandidateResponse> candidates = loadCandidates(filter);

        try (var workbook = CatRewashExcel.build(candidates)) {
            ExcelSupport.writeXlsx(response, "貓咪回洗名單.xlsx", workbook);
        }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/export.csv")
    public void exportCsv(HttpServletResponse response,
                          @RequestParam(required = false) String filter) throws IOException {
        List<CatRewashCandidateResponse> candidates = loadCandidates(filter);

        ExcelSupport.setAttachment(response, "text/csv; charset=UTF-8", "貓咪回洗名單.csv");

        // UTF-8 BOM，避免 Excel 開啟 CSV 時中文亂碼
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (PrintWriter writer = response.getWriter()) {
            writer.println(String.join(",", CatRewashExcel.HEADERS));
            for (CatRewashCandidateResponse c : candidates) {
                writer.printf("%s,%s,%s,%s,%d,%s%n",
                        csvEscape(c.getPetName()),
                        csvEscape(c.getOwnerName()),
                        csvEscape(c.getOwnerPhone()),
                        c.getLastBathDate(),
                        c.getDaysSinceLastBath(),
                        CatRewashExcel.withinWindowLabel(c));
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
