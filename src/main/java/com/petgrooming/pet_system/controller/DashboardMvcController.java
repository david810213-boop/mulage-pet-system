package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.DashboardService;
import com.petgrooming.pet_system.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardMvcController {
    private final AppointmentQueryService appointmentQueryService;
    private final PetService petService;
    private final DashboardService dashboardService;

    @GetMapping
    public String dashboard(Model model, @CurrentUser User user) {
        if (user == null) {
            return "redirect:/auth/login";
        }

        model.addAttribute("user", user);

        if (user.isStaffOrAdmin()) {
            DashboardService.StaffDashboard dashboard = dashboardService.staffDashboard();
            model.addAttribute("appointments", dashboard.appointments());
            model.addAttribute("todayCount", dashboard.todayCount());
            model.addAttribute("todayRevenue", dashboard.todayRevenue());
            model.addAttribute("pendingConfirmCount", dashboard.pendingConfirmCount());
            model.addAttribute("pendingTopupCount", dashboard.pendingTopups().size());
            model.addAttribute("memberCount", dashboard.memberCount());
            model.addAttribute("pendingAppointments", dashboard.pendingAppointments());
            model.addAttribute("pendingTopups", dashboard.pendingTopups());
            model.addAttribute("recentCustomers", dashboard.recentCustomers());
            model.addAttribute("walletsByUsername", dashboard.walletsByUsername());
            model.addAttribute("retailStockPreview", dashboard.retailStockPreview());
            model.addAttribute("retailProductCount", dashboard.retailProductCount());
            model.addAttribute("lowStockCount", dashboard.lowStockCount());
            model.addAttribute("today", dashboard.today());
        } else {
            model.addAttribute("appointments",
                    appointmentQueryService.getMyAppointments(user.getUsername()));
            model.addAttribute("pets",
                    petService.getMyPets(user.getUsername()));
        }
        return "dashboard";
    }
}
