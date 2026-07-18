package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentMvcController {

    private final AppointmentService appointmentService;
    private final GroomingService groomingItemService;
    private final UserService userService;
    private final PetService petService;

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

    // ── GET /appointments ──────────────────────────────────────────────────
    // 💡 作用：查看預約清單。不貼貼紙，因為一般會員與員工登入後都能看（各自看不同範圍）
    // 支援篩選：日期區間、狀態（已確認/已取消）、付款狀態、關鍵字（預約編號/飼主姓名/寵物名稱）
    @GetMapping
    public String list(HttpServletRequest request, Model model,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String paidStatus,
                       @RequestParam(required = false) String keyword) {
        User user = getLoginUser(request);

        model.addAttribute("user", user);

        if (user.isStaffOrAdmin()) {
            // 需求 3 / 7：店家後台看完整資訊（含內部備注、確認狀態）
            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> allAdmin =
                    appointmentService.getAllForAdmin();
            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> filteredAdmin = allAdmin.stream()
                    .filter(a -> dateFrom == null || !a.getDate().isBefore(dateFrom))
                    .filter(a -> dateTo == null || !a.getDate().isAfter(dateTo))
                    .filter(a -> status == null || status.isBlank() || a.getStatus().name().equals(status))
                    .filter(a -> paidStatus == null || paidStatus.isBlank()
                            || (paidStatus.equals("PAID") == a.isPaid()))
                    .filter(a -> keyword == null || keyword.isBlank() || matchesKeywordAdmin(a, keyword))
                    .toList();
            model.addAttribute("appointments", filteredAdmin);
        } else {
            List<com.petgrooming.pet_system.dto.AppointmentResponse> all =
                    appointmentService.getMyAppointments(user.getUsername());
            List<com.petgrooming.pet_system.dto.AppointmentResponse> filtered = all.stream()
                    .filter(a -> dateFrom == null || !a.getDate().isBefore(dateFrom))
                    .filter(a -> dateTo == null || !a.getDate().isAfter(dateTo))
                    .filter(a -> status == null || status.isBlank() || a.getStatus().name().equals(status))
                    .filter(a -> paidStatus == null || paidStatus.isBlank()
                            || (paidStatus.equals("PAID") == a.isPaid()))
                    .filter(a -> keyword == null || keyword.isBlank() || matchesKeyword(a, keyword))
                    .toList();
            model.addAttribute("appointments", filtered);
        }
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("status", status);
        model.addAttribute("paidStatus", paidStatus);
        model.addAttribute("keyword", keyword);
        return "appointments/list";
    }

    // 關鍵字比對：預約編號（AP001 或純數字）、飼主姓名、寵物名稱
    private boolean matchesKeyword(com.petgrooming.pet_system.dto.AppointmentResponse a, String keyword) {
        String kw = keyword.trim().toLowerCase();
        return a.getAppointmentCode().toLowerCase().contains(kw)
                || String.valueOf(a.getId()).equals(kw)
                || a.getOwnerName().toLowerCase().contains(kw)
                || a.getPetName().toLowerCase().contains(kw);
    }

    private boolean matchesKeywordAdmin(com.petgrooming.pet_system.dto.AppointmentAdminResponse a, String keyword) {
        String kw = keyword.trim().toLowerCase();
        return a.getAppointmentCode().toLowerCase().contains(kw)
                || String.valueOf(a.getId()).equals(kw)
                || a.getOwnerName().toLowerCase().contains(kw)
                || a.getPetName().toLowerCase().contains(kw);
    }

    // ── POST /appointments/{id}/confirm ────────────────────────────────────
    // 需求 3：店家確認預約並敲定最後時間
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(required = false)
                          @org.springframework.format.annotation.DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                          java.time.LocalDateTime confirmedTime,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.confirm(id, confirmedTime, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "已確認預約");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "確認失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── POST /appointments/{id}/notes ──────────────────────────────────────
    // 需求 7：店家設定備注（雙可見性：internalNote 店家專用 / memberNote 會員可見）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/notes")
    public String setNotes(@PathVariable Long id,
                           @RequestParam(required = false) String internalNote,
                           @RequestParam(required = false) String memberNote,
                           HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            appointmentService.setNotes(id, internalNote, memberNote, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "備注已更新");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── POST /appointments/{id}/cancel ─────────────────────────────────────
    // 💡 作用：取消預約（後台版本，表單提交）
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        try {
            var req = new com.petgrooming.pet_system.dto.CancelAppointmentRequest();
            req.setReason(reason);
            appointmentService.cancel(id, req, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "預約已取消");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "取消失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── GET /appointments/new ──────────────────────────────────────────────
    // 💡 作用：開啟預約表單。同樣只需登入，不限制特定角色。
    @GetMapping("/new")
    public String newForm(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);

        // 改用 PetResponse，前端才能讀到 sizeCategory、recommendedItemCodes
        List<PetResponse> myPets = petService.getMyPets(user.getUsername());
        List<GroomingItemResponse> availableServices = groomingItemService.getAllItems();

        model.addAttribute("user", user);
        model.addAttribute("myPets", myPets);
        model.addAttribute("appointmentRequest", new AppointmentRequest());
        model.addAttribute("groomingItems", availableServices);
        return "appointments/form";
    }

    // ── GET /appointments/slots ────────────────────────────────────────────
    // 💡 作用：查詢某日期的空檔。
    @GetMapping("/slots")
    public String slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        HttpServletRequest request, Model model) {
        User user = getLoginUser(request);

        model.addAttribute("user", user);
        model.addAttribute("slots", appointmentService.getAvailableSlots(date));
        model.addAttribute("selectedDate", date);
        return "appointments/slots";
    }

    // ── POST /appointments/submit ──────────────────────────────────────────
    // 💡 作用：送出預約表單。
    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute AppointmentRequest req,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {

        User user = getLoginUser(request);

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(user.getUsername()));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            return "appointments/form";
        }

        try {
            appointmentService.book(req, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "預約成功！");
            return "redirect:/appointments";

        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(user.getUsername()));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("errorMsg", e.getMessage());
            return "appointments/form";
        }
    }

    // ── 💡 額外加碼練習：管理員專用後台 ─────────────────────────────────────────
    // 🛡️ 啪！貼上你的自訂防偽貼紙。一般會員（CUSTOMER）如果敢打這個網址，直接在 RoleInterceptor 就會被彈飛！
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        // 這裡不需要再撈 user 來檢查了，因為警衛（RoleInterceptor）已經幫你嚴格把關！
        return "appointments/admin_dashboard";
    }
}
