package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.StoreSettingsService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
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

/**
 * 網頁版建立預約：預約表單（會員自己預約，或店家／員工代客預約）、選會員、查空檔、送出。
 */
@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentBookingMvcController {

    private final AppointmentService appointmentService;
    private final AppointmentQueryService appointmentQueryService;
    private final GroomingService groomingItemService;
    private final UserService userService;
    private final PetService petService;
    private final OperationLogService operationLogService;
    private final StoreSettingsService storeSettingsService;

    // ── GET /appointments/new ──────────────────────────────────────────────
    // 💡 作用：開啟預約表單。同樣只需登入，不限制特定角色。
    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String forUsername,
                          @CurrentUser User user) {

        // 店家/員工可代客建立預約——forUsername 有帶值時，
        // 幫「那個會員」查寵物清單，而不是查登入者自己的寵物。
        String targetUsername = user.getUsername();
        User targetUser = user;
        if (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) {
            try {
                targetUser = userService.getUserEntityByUsername(forUsername);
                targetUsername = targetUser.getUsername();
            } catch (IllegalArgumentException e) {
                return "redirect:/appointments/select-member";
            }
        }

        // 改用 PetResponse，前端才能讀到 sizeCategory、recommendedItemCodes
        List<PetResponse> myPets = petService.getMyPets(targetUsername);
        List<GroomingItemResponse> availableServices = groomingItemService.getAllItems();

        model.addAttribute("user", user);
        model.addAttribute("myPets", myPets);
        model.addAttribute("appointmentRequest", new AppointmentRequest());
        model.addAttribute("groomingItems", availableServices);
        model.addAttribute("companySignatureImage", storeSettingsService.getCompanySignatureImage());
        // 代客建立時，畫面要顯示「正在為誰建立」，送出時也要帶著這個值
        model.addAttribute("forUsername", (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) ? targetUsername : null);
        model.addAttribute("targetMemberName", (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) ? targetUser.getName() : null);
        return "appointments/form";
    }

    // ── GET /appointments/select-member ──────────────────────────────────
    // 店家代客建立預約——先搜尋/選定要幫哪位會員預約
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/select-member")
    public String selectMemberForm(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        return "appointments/select-member";
    }

    // ── GET /appointments/slots ────────────────────────────────────────────
    // 💡 作用：查詢某日期的空檔。
    @GetMapping("/slots")
    public String slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model, @CurrentUser User user) {

        model.addAttribute("user", user);
        model.addAttribute("slots", appointmentQueryService.getAvailableSlots(date));
        model.addAttribute("selectedDate", date);
        return "appointments/slots";
    }

    // ── POST /appointments/submit ──────────────────────────────────────────
    // 💡 作用：送出預約表單。
    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute AppointmentRequest req,
                         BindingResult bindingResult,
                         @RequestParam(required = false) String forUsername,
                         RedirectAttributes redirectAttributes,
                         Model model,
                         @CurrentUser User user) {


        // 代客建立時，實際預約要歸屬到目標會員，不是登入的店家帳號
        String bookingUsername = user.getUsername();
        String targetMemberName = null;
        if (forUsername != null && !forUsername.isBlank() && user.isStaffOrAdmin()) {
            try {
                var targetUser = userService.getUserEntityByUsername(forUsername);
                bookingUsername = targetUser.getUsername();
                targetMemberName = targetUser.getName();
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorMsg", "找不到指定的會員");
                return "redirect:/appointments/select-member";
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(bookingUsername));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("companySignatureImage", storeSettingsService.getCompanySignatureImage());
            model.addAttribute("forUsername", forUsername);
            model.addAttribute("targetMemberName", targetMemberName);
            String firstError = bindingResult.getAllErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("表單資料有誤，請確認後再送出");
            model.addAttribute("errorMsg", firstError);
            return "appointments/form";
        }

        try {
            AppointmentResponse res = appointmentService.book(req, bookingUsername, true);
            operationLogService.log(user, "APPOINTMENT", "BOOK",
                    "預約 " + res.getAppointmentCode(),
                    targetMemberName != null ? "代客建立（" + targetMemberName + "）：" + res.getPetName() : res.getPetName());
            redirectAttributes.addFlashAttribute("successMsg",
                    targetMemberName != null ? "已為 " + targetMemberName + " 建立預約成功！" : "預約成功！");
            return "redirect:/appointments";

        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("myPets", petService.getMyPets(bookingUsername));
            model.addAttribute("groomingItems", groomingItemService.getAllItems());
            model.addAttribute("companySignatureImage", storeSettingsService.getCompanySignatureImage());
            model.addAttribute("forUsername", forUsername);
            model.addAttribute("targetMemberName", targetMemberName);
            model.addAttribute("errorMsg", e.getMessage());
            return "appointments/form";
        }
    }
}
