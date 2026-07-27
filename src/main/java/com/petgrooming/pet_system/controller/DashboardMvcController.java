package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardMvcController {

    private final AppointmentService appointmentService;
    private final PetService petService;
    private final UserService userService;
    private final TopUpService topUpService;
    private final WalletService walletService;

    /**
     * JWT 版獲取當前登入使用者
     */
    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null)
            return null;

        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping
    public String dashboard(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }

        model.addAttribute("user", user);

        if (user.isStaffOrAdmin()) {
            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> allAdmin =
                    appointmentService.getAllForAdmin();
            model.addAttribute("appointments", allAdmin);

            LocalDate today = LocalDate.now();

            // 統計卡片
            long todayCount = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getDate().isEqual(today))
                    .count();
            long pendingConfirmCount = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getStatus().name().equals("PENDING_CONFIRM"))
                    .count();

            // 待確認預約清單（依日期時間排序，最多顯示前 8 筆）
            List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> pendingAppointments = allAdmin.stream()
                    .filter(a -> !a.isCancelled() && a.getStatus().name().equals("PENDING_CONFIRM"))
                    .sorted(Comparator.comparing(com.petgrooming.pet_system.dto.AppointmentAdminResponse::getDate)
                            .thenComparing(com.petgrooming.pet_system.dto.AppointmentAdminResponse::getStartTime))
                    .limit(8)
                    .toList();

            // 待審核儲值申請
            var pendingTopups = topUpService.pending();

            // 會員名單（含餘額，最多顯示前 8 筆，並帶出總會員數）
            var allCustomers = userService.getAllCustomers();
            var recentCustomers = allCustomers.stream().limit(8).toList();
            Map<String, com.petgrooming.pet_system.dto.WalletResponse> walletsByUsername = new HashMap<>();
            for (var c : recentCustomers) {
                walletsByUsername.put(c.getUsername(), walletService.getWallet(c.getUsername()));
            }

            model.addAttribute("todayCount", todayCount);
            model.addAttribute("pendingConfirmCount", pendingConfirmCount);
            model.addAttribute("pendingTopupCount", pendingTopups.size());
            model.addAttribute("memberCount", allCustomers.size());
            model.addAttribute("pendingAppointments", pendingAppointments);
            model.addAttribute("pendingTopups", pendingTopups);
            model.addAttribute("recentCustomers", recentCustomers);
            model.addAttribute("walletsByUsername", walletsByUsername);
            model.addAttribute("today", today);
        } else {
            model.addAttribute("appointments",
                    appointmentService.getMyAppointments(user.getUsername()));
            model.addAttribute("pets",
                    petService.getMyPets(user.getUsername()));
        }
        return "dashboard";
    }
}
