package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
    /** 零售商品沒有個別安全庫存量欄位，先以固定門檻判斷低庫存。 */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final AppointmentService appointmentService;
    private final PaymentService paymentService;
    private final WalkInOrderService walkInOrderService;
    private final TopUpService topUpService;
    private final UserService userService;
    private final WalletService walletService;
    private final RetailProductService retailProductService;

    public record StaffDashboard(
            LocalDate today,
            List<AppointmentAdminResponse> appointments,
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
        List<AppointmentAdminResponse> allAdmin = appointmentService.getAllForAdmin();
        List<AppointmentAdminResponse> upcomingPending = upcomingPendingConfirm(allAdmin, today);
        List<User> allCustomers = userService.getAllCustomers();
        List<User> recentCustomers = allCustomers.stream().limit(PREVIEW_SIZE).toList();
        List<RetailProduct> activeRetailProducts = retailProductService.listActive().stream()
                .sorted(Comparator.comparingInt(RetailProduct::getStockQuantity))
                .toList();

        return new StaffDashboard(
                today,
                allAdmin,
                allAdmin.stream().filter(a -> !a.isCancelled() && a.getDate().isEqual(today)).count(),
                todayRevenue(today),
                upcomingPending.size(),
                upcomingPending.stream()
                        .sorted(Comparator.comparing(AppointmentAdminResponse::getDate)
                                .thenComparing(AppointmentAdminResponse::getStartTime))
                        .limit(PREVIEW_SIZE)
                        .toList(),
                topUpService.pending(),
                allCustomers.size(),
                recentCustomers,
                walletsOf(recentCustomers),
                activeRetailProducts.stream().limit(PREVIEW_SIZE).toList(),
                activeRetailProducts.size(),
                activeRetailProducts.stream().filter(p -> p.getStockQuantity() <= LOW_STOCK_THRESHOLD).count());
    }

    /**
     * 待確認且日期不早於今天的預約。已經過了日期卻一直沒確認的舊預約不列入
     * （預約本身不受影響，仍可在預約列表查到）。
     */
    private static List<AppointmentAdminResponse> upcomingPendingConfirm(List<AppointmentAdminResponse> all, LocalDate today) {
        return all.stream()
                .filter(a -> !a.isCancelled() && a.getStatus().name().equals("PENDING_CONFIRM"))
                .filter(a -> !a.getDate().isBefore(today))
                .toList();
    }

    /**
     * 今日營收：預約結帳與現場單當日已付款的實收金額加總，以付款時間為準（與財務報表一致）。
     */
    private int todayRevenue(LocalDate today) {
        int appointmentRevenue = paymentService.findPaidTransactions().stream()
                .filter(t -> t.getPaymentTime() != null && t.getPaymentTime().toLocalDate().isEqual(today))
                .mapToInt(t -> t.getFinalAmount())
                .sum();
        int walkInRevenue = walkInOrderService.findAllOrders().stream()
                .filter(w -> w.isPaid() && w.getPaymentTime() != null && w.getPaymentTime().toLocalDate().isEqual(today))
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
