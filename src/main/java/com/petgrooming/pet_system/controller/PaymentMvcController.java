package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.dto.TransactionResponse;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.PricingSettings;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentDetailService;
import com.petgrooming.pet_system.service.AppointmentItemService;
import com.petgrooming.pet_system.service.CheckoutPreviewService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.MemberConsumptionService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PaymentService;
import com.petgrooming.pet_system.service.StoreSettingsService;
import com.petgrooming.pet_system.service.RetailProductService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentMvcController {

    private final PaymentService paymentService;

    private final StoreSettingsService storeSettingsService;
    private final OperationLogService operationLogService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentDetailService appointmentDetailService;
    private final AppointmentItemService appointmentItemService;
    private final CheckoutPreviewService checkoutPreviewService;
    private final RetailProductService retailProductService; // 預約結帳頁加購零售商品
    private final GroomingService groomingService; // 編輯訂單新增服務項目
    private final MemberConsumptionService memberConsumptionService;
    private final WalkInOrderService walkInOrderService;
    private final GroomingMenuFilter groomingMenuFilter;

    // 交易紀錄：預約結帳（Transaction）跟現場開單（WalkInOrder）是兩張資料表，
    // 「交易紀錄」頁面要呈現完整消費全貌，合併成同一份清單並依付款時間排序
    private List<ConsumptionRecordResponse> buildCombinedRecords(
            List<TransactionResponse> transactions,
            List<WalkInOrder> walkInOrders) {
        return memberConsumptionService.buildCombinedRecords(transactions, walkInOrders);
    }

    // 列出付款紀錄（員工/管理員看全部，顧客只看自己的）
    @GetMapping
    public String list(Model model, @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("user", user);
        if (user.isStaffOrAdmin()) {
            model.addAttribute("transactions", buildCombinedRecords(
                    paymentService.getAllTransactions(),
                    walkInOrderService.findAllOrders()));
        } else {
            model.addAttribute("transactions", buildCombinedRecords(
                    paymentService.getMyTransactions(user.getUsername()),
                    walkInOrderService.findOrdersOfMember(user.getUsername())));
        }
        return "payments/list";
    }

    // 結帳頁面（從預約列表點「結帳」連過來）
    @GetMapping("/checkout/{appointmentId}")
    public String checkoutPage(@PathVariable Long appointmentId, Model model, @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("checkoutRequest", new CheckoutRequest());
        addCheckoutBasics(model, appointmentId, user);
        addMenuAndPreview(model, appointmentId, user);
        return "payments/checkout";
    }

    /** 結帳頁一定要有的資料：付款方式、匯款帳號、加購商品清單。 */
    private void addCheckoutBasics(Model model, Long appointmentId, User user) {
        model.addAttribute("user", user);
        model.addAttribute("appointmentId", appointmentId);
        model.addAttribute("paymentMethods", CheckoutPageSupport.CHECKOUT_PAYMENT_METHODS);
        model.addAttribute("bankAccountInfo", storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
        model.addAttribute("retailProducts", retailProductService.listActive()); // 結帳頁加購商品清單
        // 自訂金額加購用的積分分類下拉選單
        model.addAttribute("performanceCategories", PerformanceCategory.values());
    }

    /**
     * 「新增服務項目」選單（依這隻寵物的體型篩選，規則見 GroomingMenuFilter）、逐項目消費明細，
     * 以及會員的儲值金餘額與折扣後金額預覽（跟實際結帳逐項目扣款用同一份資料，見 CheckoutPreviewService）。
     */
    private void addMenuAndPreview(Model model, Long appointmentId, User user) {
        boolean isExisting = appointmentQueryService.isExistingCustomerPet(appointmentId);
        String petType = appointmentQueryService.getPetTypeForAppointment(appointmentId);
        var pet = appointmentQueryService.getPetForAppointment(appointmentId);
        model.addAttribute("pet", pet);
        model.addAttribute("groomingItems", groomingMenuFilter.filterForPetShape(
                groomingService.getAllItems(), isExisting, petType, pet));
        model.addAttribute("weightMissing", groomingMenuFilter.needsWeight(petType, pet));

        var detail = appointmentDetailService.getAppointmentDetail(appointmentId, user.getUsername());
        model.addAttribute("detailItems", detail.getItems());
        Appointment appointment = appointmentQueryService.findAppointment(appointmentId).orElse(null);
        if (appointment != null && appointment.getUser() != null) {
            model.addAttribute("baseAmount", appointment.getTotalAmount());
            CheckoutPageSupport.addWalletPreview(model, checkoutPreviewService.forAppointment(
                    appointment.getUser().getUsername(), detail.getItems(), petType));
        }
    }

    // ── POST /payments/checkout/{appointmentId}/add-retail-item ────────────
    // 新增 from 參數，讓核對頁提交後也能導回核對頁，不會固定跳回結帳頁
    @PostMapping("/checkout/{appointmentId}/add-retail-item")
    public String addRetailItem(@PathVariable Long appointmentId,
                                @RequestParam Long retailProductId,
                                @RequestParam(defaultValue = "1") int quantity,
                                @RequestParam(defaultValue = "checkout") String from,
                                RedirectAttributes redirectAttributes,
                                @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";

        try {
            appointmentItemService.addRetailItem(appointmentId, retailProductId, quantity, user.getUsername());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return redirectAfterEdit(appointmentId, from);
    }

    // ── POST /payments/checkout/{appointmentId}/add-grooming-item ──────────
    // 編輯訂單——結帳前補一筆漏開/開錯的美容服務項目
    // customPrice 選填，店員手動輸入自訂價格用
    // （例如剪毛這種價格浮動的項目），空白就照項目設定的固定價格。
    @PostMapping("/checkout/{appointmentId}/add-grooming-item")
    public String addGroomingItem(@PathVariable Long appointmentId,
                                  @RequestParam Long groomingItemId,
                                  @RequestParam(required = false) Integer customPrice,
                                  @RequestParam(required = false) Long operatorStaffId, // 空白＝稍後補填
                                  @RequestParam(required = false) Long operatorStaffId2,
                                  @RequestParam(defaultValue = "checkout") String from,
                                  RedirectAttributes redirectAttributes,
                                  @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";

        try {
            appointmentItemService.addGroomingItem(appointmentId, groomingItemId, customPrice, operatorStaffId, operatorStaffId2, user.getUsername());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return redirectAfterEdit(appointmentId, from);
    }

    // ── POST /payments/checkout/{appointmentId}/add-custom-item ────────────
    // 自訂金額加購——處理「高階定制調理」開放式報價
    // 跟各種浮動加價，不綁定任何現有服務項目，店員直接輸入項目名稱+金額。
    @PostMapping("/checkout/{appointmentId}/add-custom-item")
    public String addCustomItem(@PathVariable Long appointmentId,
                                @RequestParam String itemName,
                                @RequestParam int price,
                                @RequestParam(required = false) PerformanceCategory category,
                                @RequestParam(required = false) Long operatorStaffId,
                                @RequestParam(required = false) Long operatorStaffId2,
                                @RequestParam(defaultValue = "checkout") String from,
                                RedirectAttributes redirectAttributes,
                                @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";

        try {
            appointmentItemService.addCustomItem(appointmentId, itemName, price, category, operatorStaffId, operatorStaffId2, user.getUsername());
            redirectAttributes.addFlashAttribute("successMsg", "已新增自訂項目「" + itemName + "」");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return redirectAfterEdit(appointmentId, from);
    }

    // ── POST /payments/checkout/{appointmentId}/remove-item ────────────────
    // 結帳前任何項目都能移除（零售商品、服務項目、自訂項目），
    // 供編輯訂單使用。
    @PostMapping("/checkout/{appointmentId}/remove-item")
    public String removeItem(@PathVariable Long appointmentId,
                             @RequestParam Long itemId,
                             @RequestParam(defaultValue = "checkout") String from,
                             RedirectAttributes redirectAttributes,
                             @CurrentUser User user) {
        if (user == null) return "redirect:/auth/login";

        try {
            appointmentItemService.removeItem(appointmentId, itemId, user.getUsername());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return redirectAfterEdit(appointmentId, from);
    }

    // 編輯訂單的三個端點共用的導回邏輯——from=final-check 導回核對頁，
    // 其餘（預設 checkout）導回結帳頁，避免同一段字串重複寫三次。
    private String redirectAfterEdit(Long appointmentId, String from) {
        if ("final-check".equals(from)) {
            return "redirect:/appointments/" + appointmentId + "/final-check";
        }
        return "redirect:/payments/checkout/" + appointmentId;
    }

    // 處理結帳表單提交
    @PostMapping("/checkout/{appointmentId}/submit")
    public String checkoutSubmit(@PathVariable Long appointmentId,
                                 @ModelAttribute CheckoutRequest req,
                                 RedirectAttributes redirectAttributes,
                                 Model model,
                                 @CurrentUser User user) {

        if (user == null) return "redirect:/auth/login";

        try {
            var result = paymentService.checkout(appointmentId, req, user.getUsername());
            operationLogService.log(user, "APPOINTMENT", "CHECKOUT", "預約 #" + appointmentId,
                    req.getPaymentMethod() != null ? req.getPaymentMethod().name() : null);

            CheckoutPageSupport.addWeightUpdateReminder(redirectAttributes,
                    appointmentQueryService.getPetForAppointment(appointmentId));

            // 匯款結帳當下還沒真的收到錢，只是進入「待對帳」狀態，
            // 這時候還沒有真正完成的交易紀錄可看，不該跳去交易紀錄頁；
            // 導回預約列表，等店家之後在儲值管理/匯款頁確認收款才算真的完成。
            if (result.isPaid()) {
                redirectAttributes.addFlashAttribute("successMsg", "結帳成功！");
                return "redirect:/payments";
            } else {
                redirectAttributes.addFlashAttribute("successMsg", "已送出，進入待對帳狀態，確認收到匯款後記得回來點「確認收款」");
                return "redirect:/appointments";
            }
        } catch (IllegalArgumentException e) {
            // 結帳被業務規則擋下（例如還沒核對、儲值金不足）：回到結帳頁顯示原因，保留剛才選的付款方式
            model.addAttribute("checkoutRequest", req);
            model.addAttribute("errorMsg", e.getMessage());
            addCheckoutBasics(model, appointmentId, user);
            // 組選單與金額預覽時如果又出錯（例如這筆預約資料本身有異常），不能讓第二個例外蓋掉
            // 真正的結帳錯誤訊息；最差情況就是頁面少了選單與金額預覽，但看得到「為什麼結帳失敗」。
            try {
                addMenuAndPreview(model, appointmentId, user);
            } catch (Exception previewError) {
                log.warn("結帳錯誤頁組金額預覽時另外出錯，預約 #{}，僅顯示原始錯誤訊息：{}",
                        appointmentId, previewError.getMessage(), previewError);
            }
            return "payments/checkout";
        } catch (Exception e) {
            // 保底：checkout() 本身如果拋出非預期的例外（不是業務規則的 IllegalArgumentException），
            // 不攔下來的話會變成一片空白的 Whitelabel 500 錯誤頁，使用者跟店家都看不出發生什麼事。
            // 這裡攔下來，把完整堆疊記到 log（Railway Logs 搜尋「結帳發生未預期錯誤」就找得到），
            // 畫面則導回結帳頁顯示友善訊息，至少能重試或回報。
            log.error("結帳發生未預期錯誤，預約 #{}，操作人：{}", appointmentId, user.getUsername(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "結帳發生未預期的錯誤，請重新整理後再試一次；如持續發生請聯繫系統管理員");
            return "redirect:/payments/checkout/" + appointmentId;
        }
    }

    // ── POST /payments/{id}/confirm-wire-transfer ───────────────────────
    // 店員核對匯款到帳後點擊，訂單才正式轉為已完成
    @PostMapping("/{id}/confirm-wire-transfer")
    public String confirmWireTransfer(@PathVariable Long id,
                                      RedirectAttributes redirectAttributes,
                                      @CurrentUser User user) {
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
    public String refund(@PathVariable Long id,
                         RedirectAttributes redirectAttributes,
                         @CurrentUser User user) {
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
    // 店家設定匯款帳號資訊（僅管理員可改，供結帳選匯款時自動帶出）
    // 同時管理「結帳收款」與「儲值金收款（大額專用）」兩組獨立帳戶
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/bank-account")
    public String bankAccountForm(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("checkoutInfo",
                storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT));
        model.addAttribute("topupInfo",
                storeSettingsService.getBankAccountInfo(BankAccountPurpose.TOPUP));
        return "payments/bank-account";
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bank-account")
    public String updateBankAccount(@RequestParam BankAccountPurpose purpose,
                                    @RequestParam String bankName,
                                    @RequestParam String accountNumber,
                                    @RequestParam String accountHolder, RedirectAttributes ra,
                                    @CurrentUser User user) {
        storeSettingsService.updateBankAccountInfo(purpose, bankName, accountNumber, accountHolder);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_BANK_ACCOUNT",
                purpose.getLabel() + " 帳號設定", null);
        ra.addFlashAttribute("successMsg", "已更新「" + purpose.getLabel() + "」帳號資訊");
        return "redirect:/payments/bank-account";
    }

    // ── POST /payments/bank-account/qr-code ─────────────────────────────
    // 上傳/更換匯款收款 QR Code（依用途各自獨立）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/bank-account/qr-code")
    public String uploadBankAccountQrCode(@RequestParam BankAccountPurpose purpose,
                                          @RequestParam("file") MultipartFile file, RedirectAttributes ra,
                                          @CurrentUser User user) {
        try {
            storeSettingsService.updateBankAccountQrCode(purpose, file);
            operationLogService.log(user, "APPOINTMENT", "UPDATE_BANK_ACCOUNT_QR",
                    purpose.getLabel() + " 收款 QR Code", null);
            ra.addFlashAttribute("successMsg", "已更新「" + purpose.getLabel() + "」的收款 QR Code");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", "上傳失敗：" + e.getMessage());
        }
        return "redirect:/payments/bank-account";
    }

    // ── GET /payments/company-signature ──────────────────────────────────
    // 店家上傳乙方固定電子簽名檔（顯示在顧客端契約最下方）
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/company-signature")
    public String companySignatureForm(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("signatureImage", storeSettingsService.getCompanySignatureImage());
        return "payments/company-signature";
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/company-signature")
    public String updateCompanySignature(@RequestParam String signatureImage, RedirectAttributes ra, @CurrentUser User user) {
        if (signatureImage == null || !signatureImage.startsWith("data:image")) {
            ra.addFlashAttribute("errorMsg", "請上傳有效的圖片檔案");
            return "redirect:/payments/company-signature";
        }
        storeSettingsService.updateCompanySignature(signatureImage);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_COMPANY_SIGNATURE", "乙方電子簽名檔", null);
        ra.addFlashAttribute("successMsg", "已更新乙方電子簽名檔");
        return "redirect:/payments/company-signature";
    }

    // ── GET /payments/api/company-signature ─────────────────────────────
    // 公開 API，給 LIFF 靜態頁面（booking.html）用 JS 抓取簽名檔顯示在契約最下方。
    // 不需要登入即可讀取——簽名檔本身不是敏感資訊，是要公開展示給顧客看的。
    // 注意：這個 controller class 層級是 @RequestMapping("/payments")，所以完整路徑
    // 是 /payments/api/company-signature，不是看起來很直覺的 /api/company-signature
    // （之前 booking.html 就是因為呼叫錯路徑，一直 404，簽名檔顯示不出來）。
    @GetMapping("/api/company-signature")
    @ResponseBody
    public Map<String, String> getCompanySignatureApi() {
        String image = storeSettingsService.getCompanySignatureImage();
        return Collections.singletonMap("signatureImage", image);
    }

    // ── GET /payments/pricing-settings ──────────────────────────────────
    // 貓咪體重加價門檻、狗狗小型犬/大型犬體重切點，後台可調整
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/pricing-settings")
    public String pricingSettingsForm(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("settings", storeSettingsService.getPricingSettings());
        return "payments/pricing-settings";
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/pricing-settings")
    public String updatePricingSettings(@ModelAttribute PricingSettings settings, RedirectAttributes ra, @CurrentUser User user) {
        storeSettingsService.updatePricingSettings(settings);
        operationLogService.log(user, "APPOINTMENT", "UPDATE_PRICING_SETTINGS", "體重定價門檻設定", null);
        ra.addFlashAttribute("successMsg", "已更新體重定價門檻");
        return "redirect:/payments/pricing-settings";
    }
}
