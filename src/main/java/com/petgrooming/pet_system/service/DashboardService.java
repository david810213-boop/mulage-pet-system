package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.config.StoreRules;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 員工首頁（Dashboard）的統計數字與待辦清單。 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 首頁清單最多顯示的筆數。 */
    private static final int PREVIEW_SIZE = 8;
    private final AppointmentQueryService appointmentQueryService;
    private final PaymentService paymentService;
    private final WalkInOrderService walkInOrderService;
    private final TopUpService topUpService;
    private final UserService userService;
    private final WalletService walletService;
    private final RetailProductService retailProductService;

    public record StaffDashboard(
            LocalDate today,
            List<AppointmentAdminResponse> appointments, // 今天的預約
            long todayCount,
            int todayRevenue,
            long pendingConfirmCount,
            List<AppointmentAdminResponse> pendingAppointments,
            List<TopUpRequestResponse> pendingTopups,
            int memberCount,
            List<User> recentCustomers,
            Map<String, WalletResponse> walletsByUsername,
            List<RetailProduct> retailStockPreview,
            int retailProductCount,
            long lowStockCount) {
    }

    public StaffDashboard staffDashboard() {
        LocalDate today = LocalDate.now();
        // 只查今天的預約與待確認的預約，不撈全部歷史
        List<AppointmentAdminResponse> todays = appointmentQueryService.getForAdminBetween(today, today);
        List<AppointmentAdminResponse> upcomingPending = appointmentQueryService.getPendingConfirmForAdminFrom(today);
        List<User> recentCustomers = userService.firstCustomers(PREVIEW_SIZE);
        List<RetailProduct> activeRetailProducts = retailProductService.listActive().stream()
                .sorted(Comparator.comparingInt(RetailProduct::getStockQuantity))
                .toList();

        return new StaffDashboard(
                today,
                todays,
                todays.stream().filter(a -> !a.isCancelled()).count(),
                todayRevenue(today),
                upcomingPending.size(),
                upcomingPending.stream()
                        .sorted(Comparator.comparing(AppointmentAdminResponse::getDate)
                                .thenComparing(AppointmentAdminResponse::getStartTime))
                        .limit(PREVIEW_SIZE)
                        .toList(),
                topUpService.pending(),
                (int) userService.countCustomers(),
                recentCustomers,
                walletsOf(recentCustomers),
                activeRetailProducts.stream().limit(PREVIEW_SIZE).toList(),
                activeRetailProducts.size(),
                activeRetailProducts.stream().filter(p -> p.getStockQuantity() <= StoreRules.LOW_STOCK_THRESHOLD).count());
    }

    /**
     * 今日營收：預約結帳與現場單當日已付款的實收金額加總，以付款時間為準（與財務報表一致）。
     * 首頁與預約列表的「今日營收」都用這個數字。
     */
    public int todayRevenue(LocalDate today) {
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        int appointmentRevenue = paymentService.findPaidTransactionsBetween(from, to).stream()
                .mapToInt(t -> t.getFinalAmount())
                .sum();
        int walkInRevenue = walkInOrderService.findPaidOrdersBetween(from, to).stream()
                .mapToInt(w -> w.getChargedAmount() != null ? w.getChargedAmount() : w.getTotalAmount())
                .sum();
        return appointmentRevenue + walkInRevenue;
    }

    private Map<String, WalletResponse> walletsOf(List<User> customers) {
        Map<String, WalletResponse> wallets = new HashMap<>();
        for (User customer : customers) {
            wallets.put(customer.getUsername(), walletService.getWallet(customer.getUsername()));
        }
        return wallets;
    }
}
