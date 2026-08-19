package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.UserRole;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentMvcController {

    private final PaymentService paymentService;
    private final UserService userService;
    private final WalletService walletService;
    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final OperationLogService operationLogService;
    private final com.petgrooming.pet_system.service.AppointmentService appointmentService;
    private final com.petgrooming.pet_system.service.CatRewashDiscountService catRewashDiscountService; // 需求 8-1

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
                    .sourceType("APPOINTMENT")
                    .recordId(t.getAppointmentId())
                    .code(t.getAppointmentCode())
                    .petName(t.getPetName())
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
                    .sourceType("WALKIN")
                    .recordId(w.getId())
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
        // 需求 10：結帳畫面不再提供「信用卡」選項（歷史資料仍保留該列舉值，僅畫面隱藏）
        model.addAttribute("paymentMethods", java.util.Arrays.stream(PaymentMethod.values())
                .filter(m -> m != PaymentMethod.CREDIT_CARD).toList());
        model.addAttribute("checkoutRequest", new CheckoutRequest());
        model.addAttribute("bankAccountInfo",
                paymentService.getBankAccountInfo(com.petgrooming.pet_system.enums.BankAccountPurpose.CHECKOUT));

        // 帶入該預約會員的儲值金餘額與會員折扣，讓店家/員工結帳時可預覽儲值金折扣後金額
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment != null && appointment.getUser() != null) {
            var wallet = walletService.getWallet(appointment.getUser().getUsername());
            model.addAttribute("walletBalance", wallet.getBalance());
            model.addAttribute("walletLowBalance", wallet.getBalance() < PaymentService.WALLET_LOW_BALANCE_THRESHOLD);
            model.addAttribute("walletDiscount", wallet.getDiscount());
            model.addAttribute("walletDiscountActive", wallet.isCardActive() && wallet.getDiscount() < 1.0);
            model.addAttribute("baseAmount", appointment.getTotalAmount());
        }

        // 需求：結帳頁面顯示逐項目消費明細（含折扣資格），跟實際結帳邏輯用同一份資料，
        // 避免像舊版「整筆金額×折扣」預覽跟實際逐項目扣款金額對不上。
        var detail = appointmentService.getAppointmentDetail(appointmentId, user.getUsername());
        model.addAttribute("detailItems", detail.getItems());
        if (appointment != null && appointment.getUser() != null) {
            double discount = walletService.getWallet(appointment.getUser().getUsername()).getDiscount();
            // 需求 8-1 修正：回洗優惠與會員折扣只能擇一，預覽金額改用同一套「擇一」規則計算，
            // 不再是舊版的「符合會員折扣就直接乘」。
            double walletPreview = detail.getItems().stream()
                    .mapToDouble(it -> catRewashDiscountService.resolvePreferredDiscount(
                            it.getPrice(), it.isRewashEligible(), it.isDiscountEligible(), discount).price())
                    .sum();
            model.addAttribute("walletFinalAmount", (int) Math.round(walletPreview));
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

            // 需求（追加）：組錯誤頁預覽資料本身如果又出錯（例如這筆預約資料本身有異常），
            // 不能讓第二個例外蓋掉使用者原本該看到的錯誤訊息，變成一片空白的 500 錯誤頁。
            // 這裡包一層 try/catch，最差情況就是錯誤頁少了金額預覽，但至少使用者看得到
            // 「為什麼結帳失敗」這句話，店家也才能照著訊息判斷下一步。
            try {
                Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
                if (appointment != null && appointment.getUser() != null) {
                    var wallet = walletService.getWallet(appointment.getUser().getUsername());
                    model.addAttribute("walletBalance", wallet.getBalance());
                    model.addAttribute("walletLowBalance", wallet.getBalance() < PaymentService.WALLET_LOW_BALANCE_THRESHOLD);
                    model.addAttribute("walletDiscount", wallet.getDiscount());
                    model.addAttribute("walletDiscountActive", wallet.isCardActive() && wallet.getDiscount() < 1.0);
                    model.addAttribute("baseAmount", appointment.getTotalAmount());
                    // 需求 8-1 修正：跟主要頁面用同一套「擇一」邏輯算預覽金額，避免錯誤頁顯示的金額不準
                    var detail = appointmentService.getAppointmentDetail(appointmentId, user.getUsername());
                    model.addAttribute("detailItems", detail.getItems());
                    double walletPreview = detail.getItems().stream()
                            .mapToDouble(it -> catRewashDiscountService.resolvePreferredDiscount(
                                    it.getPrice(), it.isRewashEligible(), it.isDiscountEligible(), wallet.getDiscount()).price())
                            .sum();
                    model.addAttribute("walletFinalAmount", (int) Math.round(walletPreview));
                }
            } catch (Exception previewError) {
                log.warn("結帳錯誤頁組金額預覽時另外出錯，預約 #{}，僅顯示原始錯誤訊息：{}",
                        appointmentId, previewError.getMessage(), previewError);
            }

            return "payments/checkout";
        } catch (Exception e) {
            // 保底：checkout() 本身如果拋出非預期的例外（不是業務規則的 IllegalArgumentException），
            // 原本會直接變成一片空白的 Whitelabel 500 錯誤頁，使用者跟店家都看不出發生什麼事。
            // 這裡攔下來，把完整堆疊記到 log（Railway Logs 搜尋「結帳發生未預期錯誤」就找得到），
            // 畫面則導回結帳頁顯示友善訊息，至少能重試或回報。
            log.error("結帳發生未預期錯誤，預約 #{}，操作人：{}", appointmentId, user.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "結帳發生未預期的錯誤，請重新整理後再試一次；如持續發生請聯繫系統管理員");
            return "redirect:/payments/checkout/" + appointmentId;
        }
    }

    // ── POST /payments/{id}/confirm-wire-transfer ───────────────────────
    // 需求 10：店員核對匯款到帳後點擊，訂單才正式轉為已完成
    @PostMapping("/{id}/confirm-wire-transfer")
    public String confirmWireTransfer(@PathVariable Long id, HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";

        try {
            paymentService.confirmWireTransferPayment(id, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CONFIRM_WIRE_TRANSFER", "預約 #" + id, null);
            redirectAttributes.addFlashAttribute("successMsg", "已確認收款，訂單狀態轉為已完成");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "確認失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── POST /payments/{id}/refund ────────────────────────────────────────
    // 退款：僅限「已結帳」的預約。退款後回到「已確認」狀態，清空現場開單、
    // 結束服務、核對進度，可重新開單重新結帳；管理員與員工皆可操作。
    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";

        try {
            paymentService.refund(id, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "REFUND", "預約 #" + id, null);
            redirectAttributes.addFlashAttribute("successMsg", "已退款，預約已回到「已確認」狀態，可重新開單");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "退款失敗：" + e.getMessage());
        }
        return "redirect:/appointments";
    }

    // ── GET /payments/bank-account ────────────────────────────────────────
    // 需求 10：店家設定匯款帳號資訊（僅管理員可改，供結帳選匯款時自動帶出）
    // 需求（追加）：改成同時管理「結帳收款」與「儲值金收款（大額專用）」兩組獨立帳戶
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/bank-account")
    public String bankAccountForm(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("checkoutInfo",
                paymentService.getBankAccountInfo(com.petgrooming.pet_system.enums.BankAccountPurpose.CHECKOUT));
        model.addAttribute("topupInfo",
                paymentService.getBankAccountInfo(com.petgrooming.pet_system.enums.BankAccountPurpose.TOPUP));
        return "payments/bank-account";
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bank-account")
    public String updateBankAccount(@RequestParam com.petgrooming.pet_system.enums.BankAccountPurpose purpose,
                                    @RequestParam String bankName,
                                    @RequestParam String accountNumber,
                                    @RequestParam String accountHolder,
                                    HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        paymentService.updateBankAccountInfo(purpose, bankName, accountNumber, accountHolder);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_BANK_ACCOUNT",
                purpose.getLabel() + " 帳號設定", null);
        ra.addFlashAttribute("successMsg", "已更新「" + purpose.getLabel() + "」帳號資訊");
        return "redirect:/payments/bank-account";
    }

    // ── POST /payments/bank-account/qr-code ─────────────────────────────
    // 需求 21：上傳/更換匯款收款 QR Code（依用途各自獨立）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bank-account/qr-code")
    public String uploadBankAccountQrCode(@RequestParam com.petgrooming.pet_system.enums.BankAccountPurpose purpose,
                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                          HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        try {
            paymentService.updateBankAccountQrCode(purpose, file);
            operationLogService.log(user, "APPOINTMENT", "UPDATE_BANK_ACCOUNT_QR",
                    purpose.getLabel() + " 收款 QR Code", null);
            ra.addFlashAttribute("successMsg", "已更新「" + purpose.getLabel() + "」的收款 QR Code");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", "上傳失敗：" + e.getMessage());
        }
        return "redirect:/payments/bank-account";
    }

    // ── GET /payments/company-signature ──────────────────────────────────
    // 需求 22：店家上傳乙方固定電子簽名檔（顯示在顧客端契約最下方）
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/company-signature")
    public String companySignatureForm(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("signatureImage", paymentService.getCompanySignatureImage());
        return "payments/company-signature";
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/company-signature")
    public String updateCompanySignature(@RequestParam String signatureImage,
                                         HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (signatureImage == null || !signatureImage.startsWith("data:image")) {
            ra.addFlashAttribute("errorMsg", "請上傳有效的圖片檔案");
            return "redirect:/payments/company-signature";
        }
        paymentService.updateCompanySignature(signatureImage);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_COMPANY_SIGNATURE", "乙方電子簽名檔", null);
        ra.addFlashAttribute("successMsg", "已更新乙方電子簽名檔");
        return "redirect:/payments/company-signature";
    }

    // ── GET /api/company-signature ────────────────────────────────────────
    // 需求 22：公開 API，給 LIFF 靜態頁面（booking.html）用 JS 抓取簽名檔顯示在契約最下方。
    // 不需要登入即可讀取——簽名檔本身不是敏感資訊，是要公開展示給顧客看的。
    @GetMapping("/api/company-signature")
    @ResponseBody
    public java.util.Map<String, String> getCompanySignatureApi() {
        String image = paymentService.getCompanySignatureImage();
        return java.util.Collections.singletonMap("signatureImage", image);
    }
}
