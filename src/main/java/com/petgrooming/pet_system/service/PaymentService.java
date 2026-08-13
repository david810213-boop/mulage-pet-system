package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.FinancialReportResponse;
import com.petgrooming.pet_system.dto.TransactionResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.Transaction;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;
    private final WalletService walletService;
    private final AppointmentService appointmentService;
    private final AppointmentItemRepository appointmentItemRepository;
    private final PerformanceRecordRepository performanceRecordRepository;
    private final com.petgrooming.pet_system.repository.BankAccountInfoRepository bankAccountInfoRepository;
    private final com.petgrooming.pet_system.repository.CompanySignatureRepository companySignatureRepository;
    private final com.petgrooming.pet_system.repository.GroomingItemRepository groomingItemRepository;
    private final CatRewashDiscountService catRewashDiscountService; // 需求 8-1：貓咪 90 天回洗優惠

    // 儲值金餘額低於此金額時，於後台顯示提醒店家「該通知會員儲值」的警示門檻
    public static final int WALLET_LOW_BALANCE_THRESHOLD = 2000;

    // ── 1. 結帳 ────────────────────────────────────────────────────────────
    @Transactional
    public TransactionResponse checkout(Long appointmentId,
                                        CheckoutRequest req,
                                        String username) {
        // 1a. 確認預約存在
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        // 1b. 確認使用者與權限
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        boolean isOwner       = appointment.getUser().getId().equals(user.getId());
        boolean isStaffOrAdmin = user.isStaffOrAdmin();

        if (!isOwner && !isStaffOrAdmin) {
            throw new IllegalArgumentException("權限不足：只能結自己的帳");
        }

        // 1c. 確認尚未付款
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已完成結帳");
        }

        // 1c-2. 必須先完成「進行中核對」（店員與家長現場核對本次美容狀況並簽名），才能結帳
        if (!appointment.isFinalCheckDone()) {
            throw new IllegalArgumentException("請先完成進行中預約的核對（美容狀況備註 + 家長簽名）後才能結帳");
        }

        // 1d. 確認沒有重複交易紀錄
        if (transactionRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new IllegalArgumentException("此預約已有交易紀錄");
        }

        // 1e. 計算最終金額
        // baseAmount 維持「帳面合計」（未打任何折扣的原始金額），呼應需求 5 已建立的
        // 「帳面合計／實際扣款」透明化顯示慣例；實際折扣一律反映在 finalAmount。
        int baseAmount  = appointment.getTotalAmount();
        // 需求 8-1：貓咪距上次洗澡未滿 90 天再預約洗澡，洗澡項目結帳自動打 9 折
        // （不限付款方式；跟需求 5 的儲值金會員折扣是兩套獨立規則，此為服務促銷、非付款方式優惠）
        int finalAmount = req.getPaymentMethod()
                .calculateFinalAmount(calculateAmountWithRewashDiscount(appointment));

        // 1e-1. 若選擇「儲值金」付款：
        //   - 僅限店家/員工於後台操作（顧客不可自行使用此付款方式）
        //   - 套用會員等級折扣（現金/信用卡/LinePay 不套用會員折扣）
        //   - 折扣後金額才是實際扣款與交易紀錄的最終金額
        if (req.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WALLET) {
            if (!isStaffOrAdmin) {
                throw new IllegalArgumentException("儲值金結帳僅限店家/員工於後台操作");
            }
            double discount = walletService.getWallet(appointment.getUser().getUsername()).getDiscount();
            // 需求 5：改成逐項目判斷是否可享折扣，不再對整筆金額統一打折。
            // 洗澡/剪毛/調理類項目打折，剪指甲/局部修剪/除廢毛等加購項目維持原價。
            // 需求 8-1：貓咪回洗優惠與會員儲值折扣疊加計算（兩者是不同性質的折扣，疊加後再扣款）。
            finalAmount = calculateWalletAmountPerItem(appointment, discount);
            walletService.deduct(appointment.getUser().getUsername(), finalAmount, appointment.getId());
        }

        // 1f. 決定經手人
        String handledBy = isOwner
                ? "會員自助（" + user.getName() + "）"
                : "員工：" + user.getName();

        // 1g. 建立交易紀錄
        // 需求 10：選「匯款」時，訂單先進入「待對帳」狀態（paid=false），
        // 要等店員另外按「確認收款」才算真正完成；其他付款方式維持原本立即完成的邏輯。
        boolean isWireTransfer = req.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WIRE_TRANSFER;

        Transaction transaction = Transaction.builder()
                .appointment(appointment)
                .user(appointment.getUser())
                .paymentMethod(req.getPaymentMethod())
                .baseAmount(baseAmount)
                .finalAmount(finalAmount)
                .paid(!isWireTransfer)
                .paymentTime(isWireTransfer ? null : LocalDateTime.now())
                .handledBy(handledBy)
                .build();

        transactionRepository.save(transaction);

        if (isWireTransfer) {
            // 匯款：先不動預約的 paid/status，也先不發放績效積分，等確認收款後才處理
            log.info("預約 #{} 選擇匯款結帳，進入待對帳狀態，等待店家確認收款", appointment.getId());
            return TransactionResponse.from(transaction);
        }

        // 1h. 將預約標記為已付款，並把狀態轉為「已完成」（結帳＝整筆服務結束）
        appointment.setPaid(true);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        // 1i. 自動建立績效紀錄（依預約選擇的服務項目 + 負責員工）
        autoCreatePerformanceRecords(appointment, user, isStaffOrAdmin);

        return TransactionResponse.from(transaction);
    }

    // ── 1x-1. 確認匯款收款：店員核對銀行入帳後，訂單才正式轉為已完成 ──────
    @Transactional
    public TransactionResponse confirmWireTransferPayment(Long appointmentId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可確認收款");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        Transaction transaction = transactionRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此預約的交易紀錄"));

        if (transaction.getPaymentMethod() != com.petgrooming.pet_system.enums.PaymentMethod.WIRE_TRANSFER) {
            throw new IllegalArgumentException("此交易不是匯款付款，無需確認收款");
        }
        if (transaction.isPaid()) {
            throw new IllegalArgumentException("此筆匯款已確認收款過，請勿重複操作");
        }

        transaction.setPaid(true);
        transaction.setPaymentTime(LocalDateTime.now());
        transactionRepository.save(transaction);

        appointment.setPaid(true);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        boolean isStaffOrAdmin = true; // 確認收款一定是店家/員工操作
        autoCreatePerformanceRecords(appointment, staff, isStaffOrAdmin);

        log.info("預約 #{} 匯款收款已確認，操作人：{}", appointmentId, username);
        return TransactionResponse.from(transaction);
    }

    /**
     * 結帳後自動依 selectedItems 建立績效紀錄
     * - 如果預約有指定 staff，用該員工
     * - 如果是 STAFF/ADMIN 操作結帳，且預約沒有指定員工，用操作結帳的人
     * - 同時自動記錄「完成」積分
     */
    private void autoCreatePerformanceRecords(Appointment appointment,
                                              User checkoutUser,
                                              boolean isStaffOrAdmin) {
        // 若此預約已透過「現場開單（依預約編號）」確認過服務項目，
        // 積分改依各項目個別指定的經手人計算，不再整包算給單一 appointment.staff。
        if (appointmentService.hasCheckinOrderItems(appointment.getId())) {
            appointmentService.awardPendingItemPointsForAppointment(appointment.getId());
        } else {
            // 舊版相容邏輯：沒有現場開單項目的預約（例如舊資料），維持原本整包算給
            // appointment.staff（或操作結帳的店家/員工）的方式，避免破壞既有資料。
            // 決定績效歸屬的員工
            User staff = appointment.getStaff();
            if (staff == null && isStaffOrAdmin) {
                staff = checkoutUser;
            }
            if (staff == null) {
                log.warn("預約 #{} 無指定員工，跳過自動績效建立", appointment.getId());
                return;
            }

            List<GroomingItem> items = appointment.getSelectedItems();
            if (items == null || items.isEmpty()) {
                log.warn("預約 #{} 無服務項目，跳過績效建立", appointment.getId());
                return;
            }

            // 依每個服務項目建立一筆績效紀錄（OTHER 類別跳過）
            for (GroomingItem item : items) {
                if (item.getPerformanceCategory() == PerformanceCategory.OTHER) continue;
                if (item.getPoints() <= 0) continue;

                performanceService.addRecord(
                        staff.getId(),
                        appointment.getId(),
                        item.getPerformanceCategory(),
                        item.getPoints(),
                        appointment.getDate(),
                        "自動計算：" + item.getName()
                );
            }
        }

        // 「完成」積分現在改由「結束服務」步驟發放（見 AppointmentService.endService），
        // 結帳這裡不再重複發放，避免同一筆預約算兩次完成積分。

        log.info("預約 #{} 結帳後自動建立績效紀錄", appointment.getId());
    }

    // ── 1x. 退款：僅限「已結帳」的預約 ────────────────────────────────────
    // 退款後預約回到「已確認」狀態、清空現場開單/服務進度，讓店家可以重新
    // 開單重新結帳（例如漏 key 項目需要重開）。
    //   - 若原本用儲值金付款，金額自動退回會員儲值餘額
    //   - 已經記給員工的積分（接進/完成/接出/服務項目）全部一併刪除
    //   - 管理員與員工皆可操作
    @Transactional
    public void refund(Long appointmentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!user.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作退款");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (!appointment.isPaid()) {
            throw new IllegalArgumentException("此預約尚未結帳，無法退款");
        }

        Transaction transaction = transactionRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此預約的交易紀錄"));

        // 1. 若原本用儲值金付款，退回儲值餘額
        if (transaction.getPaymentMethod() == PaymentMethod.WALLET) {
            walletService.refund(
                    appointment.getUser().getUsername(),
                    transaction.getFinalAmount(),
                    appointmentId,
                    "預約 #" + appointmentId + " 退款");
        }

        // 2. 刪除這筆預約已經記過的所有績效積分（接進/完成/接出/服務項目）
        List<com.petgrooming.pet_system.model.PerformanceRecord> records =
                performanceRecordRepository.findByAppointmentId(appointmentId);
        if (!records.isEmpty()) {
            performanceRecordRepository.deleteAll(records);
        }

        // 3. 刪除現場開單（依預約編號）已勾選的服務項目，讓店家可以重新開單
        List<com.petgrooming.pet_system.model.AppointmentItem> items =
                appointmentItemRepository.findByAppointmentId(appointmentId);
        if (!items.isEmpty()) {
            appointmentItemRepository.deleteAll(items);
        }

        // 4. 刪除交易紀錄（checkout() 會擋「已有交易紀錄」，必須刪掉才能重新結帳）
        transactionRepository.delete(transaction);

        // 5. 預約狀態回到「已確認」，清空核對／結束服務／現場開單的進度旗標
        appointment.setPaid(false);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setCheckinOrderConfirmed(false);
        appointment.setServiceEndedDone(false);
        appointment.setServiceEndedStaff(null);
        appointment.setServiceEndedAt(null);
        appointment.setFinalCheckDone(false);
        appointment.setFinalCheckStaff(null);
        appointment.setFinalCheckNote(null);
        appointment.setFinalCheckSignatureImage(null);
        appointment.setFinalCheckAt(null);
        appointmentRepository.save(appointment);

        log.info("預約 #{} 已退款，操作人：{}", appointmentId, username);
    }

    // ── 2. 查詢自己的交易紀錄 ──────────────────────────────────────────────
    public List<TransactionResponse> getMyTransactions(String username) {
        return transactionRepository.findByUserUsername(username)
                .stream().map(TransactionResponse::from).toList();
    }

    // ── 3. 查詢所有交易（STAFF/ADMIN）──────────────────────────────────────
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll()
                .stream().map(TransactionResponse::from).toList();
    }

    // ── 4. 財務報告（ADMIN）────────────────────────────────────────────────
    public FinancialReportResponse getFinancialReport() {
        List<Transaction> all = transactionRepository.findAll();
        List<TransactionResponse> details = all.stream()
                .map(TransactionResponse::from).toList();

        int paidCount  = (int) all.stream().filter(Transaction::isPaid).count();
        double revenue = transactionRepository.calculateTotalRevenue();
        double average = paidCount > 0 ? revenue / paidCount : 0;

        return new FinancialReportResponse(
                LocalDateTime.now(), all.size(), paidCount, revenue, average, details);
    }

    // ── 需求 10：店家匯款帳號資訊（單例設定，供結帳畫面選匯款時自動帶出） ──
    public com.petgrooming.pet_system.model.BankAccountInfo getBankAccountInfo() {
        return bankAccountInfoRepository.findAll().stream().findFirst()
                .orElse(com.petgrooming.pet_system.model.BankAccountInfo.builder()
                        .bankName("尚未設定").accountNumber("尚未設定").accountHolder("尚未設定").build());
    }

    @Transactional
    public void updateBankAccountInfo(String bankName, String accountNumber, String accountHolder) {
        com.petgrooming.pet_system.model.BankAccountInfo info = bankAccountInfoRepository.findAll()
                .stream().findFirst()
                .orElse(com.petgrooming.pet_system.model.BankAccountInfo.builder().build());
        info.setBankName(bankName);
        info.setAccountNumber(accountNumber);
        info.setAccountHolder(accountHolder);
        bankAccountInfoRepository.save(info);
    }

    // ── 需求 5：儲值金結帳金額計算，逐項目判斷是否可享折扣 ───────────────
    // 優先用現場開單（依預約編號）的實際項目；沒有的話退回顧客線上勾選的項目。
    // 需求 8-1 修正：回洗優惠與會員折扣只能擇一（取較優惠者），不再疊加相乘。
    private int calculateWalletAmountPerItem(Appointment appointment, double discount) {
        List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems =
                appointmentItemRepository.findByAppointmentId(appointment.getId());

        boolean rewashEligible = catRewashDiscountService.isRewashEligible(appointment); // 需求 8-1

        double total = 0;
        if (!checkinItems.isEmpty()) {
            for (com.petgrooming.pet_system.model.AppointmentItem item : checkinItems) {
                boolean memberEligible = item.getGroomingItemId() != null
                        && groomingItemRepository.findById(item.getGroomingItemId())
                                .map(com.petgrooming.pet_system.model.GroomingItem::isDiscountEligible)
                                .orElse(true);
                boolean rewashApplicable = rewashEligible
                        && catRewashDiscountService.isCatBathCategory(item.getPerformanceCategory());
                total += catRewashDiscountService.resolvePreferredDiscount(
                        item.getPrice(), rewashApplicable, memberEligible, discount).price();
            }
        } else {
            for (com.petgrooming.pet_system.model.GroomingItem item : appointment.getSelectedItems()) {
                double price = item.getPrice() != null ? item.getPrice() : 0;
                boolean rewashApplicable = rewashEligible && catRewashDiscountService.isCatBathItem(item);
                total += catRewashDiscountService.resolvePreferredDiscount(
                        price, rewashApplicable, item.isDiscountEligible(), discount).price();
            }
        }
        return (int) Math.round(total);
    }

    // ── 需求 8-1：非儲值金付款的一般結帳金額計算，套用貓咪 90 天回洗優惠 ──
    // 邏輯與 calculateWalletAmountPerItem 同樣優先用現場開單項目，但沒有會員等級折扣，
    // 只套用回洗優惠（不符合資格的話金額等同原本的 appointment.getTotalAmount()）。
    private int calculateAmountWithRewashDiscount(Appointment appointment) {
        boolean rewashEligible = catRewashDiscountService.isRewashEligible(appointment);
        if (!rewashEligible) {
            return appointment.getTotalAmount();
        }

        List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems =
                appointmentItemRepository.findByAppointmentId(appointment.getId());

        double total = 0;
        if (!checkinItems.isEmpty()) {
            for (com.petgrooming.pet_system.model.AppointmentItem item : checkinItems) {
                double price = item.getPrice();
                if (catRewashDiscountService.isCatBathCategory(item.getPerformanceCategory())) {
                    price *= CatRewashDiscountService.REWASH_DISCOUNT_RATE;
                }
                total += price;
            }
        } else {
            for (com.petgrooming.pet_system.model.GroomingItem item : appointment.getSelectedItems()) {
                double price = item.getPrice() != null ? item.getPrice() : 0;
                if (catRewashDiscountService.isCatBathItem(item)) {
                    price *= CatRewashDiscountService.REWASH_DISCOUNT_RATE;
                }
                total += price;
            }
        }
        return (int) Math.round(total);
    }

    // ── 需求 22：乙方（店家）固定電子簽名檔，顯示在顧客端契約最下方 ──────
    public String getCompanySignatureImage() {
        return companySignatureRepository.findAll().stream()
                .findFirst()
                .map(com.petgrooming.pet_system.model.CompanySignature::getSignatureImage)
                .orElse(null);
    }

    @Transactional
    public void updateCompanySignature(String base64Image) {
        com.petgrooming.pet_system.model.CompanySignature sig = companySignatureRepository.findAll()
                .stream().findFirst()
                .orElse(com.petgrooming.pet_system.model.CompanySignature.builder().build());
        sig.setSignatureImage(base64Image);
        companySignatureRepository.save(sig);
    }
}