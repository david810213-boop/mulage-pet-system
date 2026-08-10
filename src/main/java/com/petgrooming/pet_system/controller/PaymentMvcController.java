package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentMvcController {

    private final PaymentService paymentService;
    private final UserService userService;
    private final WalletService walletService;
    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final OperationLogService operationLogService;

    /**
     * JWT 版獲取當前登入使用者
     * 從 LoginInterceptor 存入的 request attribute 拿取 username，
     * 再用 UserService 查出完整的 User entity，沒有就回傳 null
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

    // 交易紀錄輔助方法：把預約結帳 (Transaction) 跟現場開單 (WalkInOrder) 統一格式並合併排序
    // 這兩者原本是分開的資料表（現場開單獨立設計，不動既有結帳流程），
    // 但「交易紀錄」頁面應該呈現完整消費全貌，所以這裡合併顯示。
    private java.util.List<ConsumptionRecordResponse> buildCombinedRecords(
            java.util.List<com.petgrooming.pet_system.dto.TransactionResponse> transactions,
            java.util.List<com.petgrooming.pet_system.model.WalkInOrder> walkInOrders) {

        java.util.List<ConsumptionRecordResponse> records = new java.util.ArrayList<>();

        for (var t : transactions) {
            if (!t.isPaid()) continue;
            records.add(ConsumptionRecordResponse.builder()
                    .sourceLabel("預約結帳")
                    .code(t.getAppointmentCode())
                    .petName(null)
                    .time(t.getPaymentTime())
                    .handledBy(t.getHandledBy())
                    .paymentMethodLabel(t.getPaymentMethod() != null ? t.getPaymentMethod().getDisplayName() : "—")
                    .amount(t.getFinalAmount())
                    .paid(true)
                    .build());
        }

        for (var w : walkInOrders) {
            if (!w.isPaid()) continue;
            records.add(ConsumptionRecordResponse.builder()
                    .sourceLabel("現場開單")
                    .code("現場單#" + w.getId())
                    .petName(w.getPetName())
                    .time(w.getPaymentTime())
                    .handledBy(w.getCreatedBy())
                    .paymentMethodLabel(w.getPaymentMethod() != null ? w.getPaymentMethod().getDisplayName() : "—")
                    .amount(w.getTotalAmount())
                    .paid(true)
                    .build());
        }

        records.sort((a, b) -> {
            if (a.getTime() == null && b.getTime() == null) return 0;
            if (a.getTime() == null) return 1;
            if (b.getTime() == null) return -1;
            return b.getTime().compareTo(a.getTime());
        });
        return records;
    }

    // 列出付款紀錄（員工/管理員看全部，顧客只看自己的）
    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("user", user);
        if (user.isStaffOrAdmin()) {
            model.addAttribute("transactions", buildCombinedRecords(
                    paymentService.getAllTransactions(),
                    walkInOrderRepository.findAllByOrderByCreatedAtDesc()));
        } else {
            model.addAttribute("transactions", buildCombinedRecords(
                    paymentService.getMyTransactions(user.getUsername()),
                    walkInOrderRepository.findByMemberUsernameOrderByCreatedAtDesc(user.getUsername())));
        }
        return "payments/list";
    }

    // 結帳頁面（從預約列表點「結帳」連過來）
    @GetMapping("/checkout/{appointmentId}")
    public String checkoutPage(@PathVariable Long appointmentId,
                               HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("user", user);
        model.addAttribute("appointmentId", appointmentId);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("checkoutRequest", new CheckoutRequest());

        // 帶入該預約會員的儲值金餘額與會員折扣，讓店家/員工結帳時可預覽儲值金折扣後金額
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment != null && appointment.getUser() != null) {
            var wallet = walletService.getWallet(appointment.getUser().getUsername());
            model.addAttribute("walletBalance", wallet.getBalance());
            model.addAttribute("walletLowBalance", wallet.getBalance() < PaymentService.WALLET_LOW_BALANCE_THRESHOLD);
            model.addAttribute("walletDiscount", wallet.getDiscount());
            model.addAttribute("walletDiscountActive", wallet.isCardActive() && wallet.getDiscount() < 1.0);
            model.addAttribute("baseAmount", appointment.getTotalAmount());
            model.addAttribute("walletFinalAmount",
                    (int) Math.round(appointment.getTotalAmount() * wallet.getDiscount()));
        }

        return "payments/checkout";
    }

    // 處理結帳表單提交
    @PostMapping("/checkout/{appointmentId}/submit")
    public String checkoutSubmit(@PathVariable Long appointmentId,
                                 @ModelAttribute CheckoutRequest req,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {

        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";

        try {
            paymentService.checkout(appointmentId, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CHECKOUT", "預約 #" + appointmentId,
                    req.getPaymentMethod() != null ? req.getPaymentMethod().name() : null);
            redirectAttributes.addFlashAttribute("successMsg", "結帳成功！");
            return "redirect:/payments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("appointmentId", appointmentId);
            model.addAttribute("paymentMethods", PaymentMethod.values());
            model.addAttribute("errorMsg", e.getMessage());

            Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
            if (appointment != null && appointment.getUser() != null) {
                var wallet = walletService.getWallet(appointment.getUser().getUsername());
                model.addAttribute("walletBalance", wallet.getBalance());
                model.addAttribute("walletLowBalance", wallet.getBalance() < PaymentService.WALLET_LOW_BALANCE_THRESHOLD);
                model.addAttribute("walletDiscount", wallet.getDiscount());
                model.addAttribute("walletDiscountActive", wallet.isCardActive() && wallet.getDiscount() < 1.0);
                model.addAttribute("baseAmount", appointment.getTotalAmount());
                model.addAttribute("walletFinalAmount",
                        (int) Math.round(appointment.getTotalAmount() * wallet.getDiscount()));
            }

            return "payments/checkout";
        }
    }
}
