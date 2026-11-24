package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.GroomingCategoryView;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.GroomingItemRequest;
import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.dto.UpdateGroomingItemRequest;
import com.petgrooming.pet_system.enums.AdminGroomingCategory;
import com.petgrooming.pet_system.enums.DogWeightTier;
import com.petgrooming.pet_system.export.ExcelSupport;
import com.petgrooming.pet_system.export.FinancialReportExcel;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.FinancialReportService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMvcController {

    // 存檔後回到原本的區塊，不用從頁首重新往下捲
    private static final String STAFF_SECTION = "redirect:/admin#staff";
    private static final String ITEMS_SECTION = "redirect:/admin#items";
    private static final List<Integer> POINT_OPTIONS = List.of(0, 5, 10, 15, 20, 25, 30, 35);


    private final UserService userService;
    private final FinancialReportService financialReportService;
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
    public String adminHome(HttpServletRequest request, Model model, @CurrentUser User user) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        
        addHomeModel(model, user);
        return "admin/home";
    }

    // 處理新增員工表單提交
    @PostMapping("/staff/submit")
    public String createStaff(@Valid @ModelAttribute CreateStaffRequest req,
                              BindingResult bindingResult,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                              Model model,
                              @CurrentUser User user) {

        if (!isAdmin(request)) return "redirect:/dashboard";

        if (bindingResult.hasErrors()) {
            addHomeModel(model, user);
            return "admin/home";
        }

        try {
            userService.createStaff(req);
            redirectAttributes.addFlashAttribute("successMsg", "員工帳號新增成功");
            return STAFF_SECTION;
        } catch (IllegalArgumentException e) {
            addHomeModel(model, user);
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /** 新增代班（只填姓名、選填電話，不能登入，只供選為經手人記積分）。 */
    @PostMapping("/staff/part-time")
    public String createPartTimeStaff(@RequestParam String name,
                                      @RequestParam(required = false) String phone,
                                      HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        try {
            userService.createPartTimeStaff(name, phone);
            redirectAttributes.addFlashAttribute("successMsg", "已新增代班：" + name.trim());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return STAFF_SECTION;
    }

    /** 停用員工：不能登入、不出現在經手人選單，積分紀錄保留。 */
    @PostMapping("/staff/{id}/deactivate")
    public String deactivateStaff(@PathVariable Long id, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes, @CurrentUser User user) {
        return setStaffActive(id, false, request, redirectAttributes, user);
    }

    @PostMapping("/staff/{id}/activate")
    public String activateStaff(@PathVariable Long id, HttpServletRequest request,
                                RedirectAttributes redirectAttributes, @CurrentUser User user) {
        return setStaffActive(id, true, request, redirectAttributes, user);
    }

    private String setStaffActive(Long id, boolean active, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes, User user) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        try {
            userService.setStaffActive(id, active, user);
            redirectAttributes.addFlashAttribute("successMsg", active ? "已重新啟用" : "已停用");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return STAFF_SECTION;
    }

    /** 設為管理員／取消管理員（員工要登出再登入才會套用新權限）。 */
    @PostMapping("/staff/{id}/admin")
    public String setStaffAdmin(@PathVariable Long id, @RequestParam boolean admin,
                                HttpServletRequest request, RedirectAttributes redirectAttributes,
                                @CurrentUser User user) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        try {
            userService.setStaffAdmin(id, admin, user);
            redirectAttributes.addFlashAttribute("successMsg",
                    (admin ? "已設為管理員" : "已取消管理員") + "，請對方登出再重新登入才會套用");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return STAFF_SECTION;
    }

    /** 代班轉正職：補設登入帳號密碼。 */
    @PostMapping("/staff/{id}/promote")
    public String promotePartTimeStaff(@PathVariable Long id,
                                       @RequestParam String username,
                                       @RequestParam String password,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        try {
            userService.promotePartTimeStaff(id, username, password);
            redirectAttributes.addFlashAttribute("successMsg", "已轉為正職，可以用新帳號登入");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return STAFF_SECTION;
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
        return STAFF_SECTION;
    }

    /**
     * 2. 處理【修改】美容項目表單提交
     */
    @PostMapping("/grooming/update/{id}")
    public String updateItem(@PathVariable Long id,
                             @Valid @ModelAttribute("updateGroomingItemRequest") UpdateGroomingItemRequest req,
                             BindingResult bindingResult,
                             HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                             Model model,
                             @CurrentUser User user) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";

        // 如果管理員欄位填錯（例如價格填負數）
        if (bindingResult.hasErrors()) {
            addHomeModel(model, user);
            model.addAttribute("errorMsg", "修改失敗：" + describeErrors(bindingResult));
            return "admin/home"; // 留在原地
        }

        try {
            groomingService.updateItem(id, req);
            redirectAttributes.addFlashAttribute("successMsg", "服務項目修改成功！");
            return ITEMS_SECTION;
        } catch (IllegalArgumentException e) {
            addHomeModel(model, user);
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
        return ITEMS_SECTION;
    }

    // 財務報表頁面
    @GetMapping("/report")
    public String report(HttpServletRequest request, Model model, @CurrentUser User user) {
        if (!isAdmin(request)) return "redirect:/dashboard";
        model.addAttribute("user", user);
        model.addAttribute("report", financialReportService.getFinancialReport());
        return "admin/report";
    }

    // ── GET /admin/report/export.xlsx ───────────────────────────────────
    // 財務報表匯出 Excel（當日/當月彙總 + 成本毛利 + 逐筆明細，三個工作表）
    @GetMapping("/report/export.xlsx")
    public void exportReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isAdmin(request)) {
            response.sendRedirect("/dashboard");
            return;
        }
        var report = financialReportService.getFinancialReport();
        try (var workbook = FinancialReportExcel.build(report)) {
            ExcelSupport.writeXlsx(response, "財務報表_" + LocalDate.now() + ".xlsx", workbook);
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
                                    Model model,
                                    @CurrentUser User user) {
        
        if (!isAdmin(request)) return "redirect:/dashboard";

        // 欄位校正，萬一管理員沒填名稱或價格寫負數
        if (bindingResult.hasErrors()) {
            addHomeModel(model, user);
            model.addAttribute("errorMsg", "新增失敗：" + describeErrors(bindingResult));
            return "admin/home"; // 留在原地改考卷
        }

        try {
            groomingService.createItem(req);
            redirectAttributes.addFlashAttribute("successMsg", "\u2728 成功建立新的美容項目！");
            return ITEMS_SECTION;
        } catch (IllegalArgumentException e) {
            addHomeModel(model, user);
            model.addAttribute("errorMsg", e.getMessage());
            return "admin/home";
        }
    }

    /** 管理首頁需要的資料；表單驗證失敗重新顯示時保留使用者剛填的內容。 */
    private void addHomeModel(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("allUsers", userService.getAllUsers());
        model.addAttribute("allStaff", userService.getStaffAndAdmins());
        if (!model.containsAttribute("createStaffRequest")) {
            model.addAttribute("createStaffRequest", new CreateStaffRequest());
        }
        if (!model.containsAttribute("groomingItemRequest")) {
            model.addAttribute("groomingItemRequest", new GroomingItemRequest());
        }
        List<GroomingItemResponse> items = groomingService.getAllItems();
        model.addAttribute("groomingItems", items);
        Map<Long, String> categoryOf = new HashMap<>();
        Map<AdminGroomingCategory, Long> counts = new EnumMap<>(AdminGroomingCategory.class);
        for (GroomingItemResponse item : items) {
            AdminGroomingCategory c = AdminGroomingCategory.of(item);
            categoryOf.put(item.getId(), c.name());
            counts.merge(c, 1L, Long::sum);
        }
        model.addAttribute("itemCategoryOf", categoryOf);
        model.addAttribute("itemCategories", Arrays.stream(AdminGroomingCategory.values())
                .map(c -> new GroomingCategoryView(c.name(), c.getLabel(), counts.getOrDefault(c, 0L), c.isDog()))
                .toList());
        model.addAttribute("dogTiers", DogWeightTier.values());
        model.addAttribute("pointOptions", POINT_OPTIONS);
    }

    private static final Map<String, String> GROOMING_FIELD_LABELS = Map.of(
            "itemCode", "項目代碼",
            "name", "項目名稱",
            "description", "說明描述",
            "price", "定價",
            "points", "積分分類",
            "performanceCategory", "績效分類",
            "applicablePetType", "適用物種");

    /** 把表單驗證錯誤整理成店家看得懂的一句話，例如「定價：價格必須大於 0」。 */
    private static String describeErrors(BindingResult bindingResult) {
        String detail = bindingResult.getFieldErrors().stream()
                .map(e -> {
                    String label = GROOMING_FIELD_LABELS.getOrDefault(e.getField(), e.getField());
                    // 型別轉換失敗（例如定價填了文字）時預設訊息是英文例外，改成固定說法
                    String msg = e.isBindingFailure() ? "格式不正確" : e.getDefaultMessage();
                    return label + "：" + msg;
                })
                .distinct()
                .collect(Collectors.joining("；"));
        return detail.isEmpty() ? "請檢查輸入欄位是否正確" : detail;
    }
}
