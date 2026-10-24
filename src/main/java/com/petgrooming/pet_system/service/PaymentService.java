package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CheckoutRequest;
import com.petgrooming.pet_system.dto.TransactionResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.Transaction;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.service.discount.DiscountCalculator;
import com.petgrooming.pet_system.service.discount.EligibleOffers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final GroomingItemRepository groomingItemRepository;
    private final DiscountCalculator discountCalculator; // 首次體驗／回洗優惠與會員折扣擇優
    private final RetailProductService retailProductService; // 需求（追加）：預約結帳頁加購零售商品，結帳時扣庫存

    // 儲值金餘額低於此金額時，於後台顯示提醒店家「該通知會員儲值」的警示門檻
    public static final int WALLET_LOW_BALANCE_THRESHOLD = 2000;

    // ── 1. 結帳 ────────────────────────────────────────────────────────────
    /**
     * 預約結帳。匯款先進入「待對帳」（交易未付款、預約維持未完成），等店家確認收款後才完成；
     * 其他付款方式立即完成並發放績效積分。任何一步失敗整筆退回（含零售商品庫存）。
     */
    @Transactional
    public TransactionResponse checkout(Long appointmentId, CheckoutRequest req, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        boolean isOwner = appointment.getUser().getId().equals(user.getId());
        ensureCheckoutAllowed(appointment, user, isOwner);

        deductRetailStock(appointmentId);
        PaymentMethod method = req.getPaymentMethod();
        int finalAmount = chargeAmount(appointment, method, user.isStaffOrAdmin());

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .appointment(appointment)
                .user(appointment.getUser())
                .paymentMethod(method)
                .baseAmount(appointment.getTotalAmount())
                .finalAmount(finalAmount)
                .paid(method != PaymentMethod.WIRE_TRANSFER)
                .paymentTime(method == PaymentMethod.WIRE_TRANSFER ? null : LocalDateTime.now())
                .handledBy(isOwner ? "會員自助（" + user.getName() + "）" : "員工：" + user.getName())
                .build());

        if (method == PaymentMethod.WIRE_TRANSFER) {
            log.info("預約 #{} 選擇匯款結帳，進入待對帳狀態，等待店家確認收款", appointment.getId());
            return TransactionResponse.from(transaction);
        }
        appointment.setPaid(true);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
        autoCreatePerformanceRecords(appointment, user, user.isStaffOrAdmin());
        return TransactionResponse.from(transaction);
    }

    /** 只有預約本人或員工能結帳；必須尚未付款、已完成現場核對（美容狀況備註＋家長簽名），且沒有交易紀錄。 */
    private void ensureCheckoutAllowed(Appointment appointment, User user, boolean isOwner) {
        if (!isOwner && !user.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：只能結自己的帳");
        }
        if (appointment.isPaid()) {
            throw new PaymentException("此預約已完成結帳");
        }
        if (!appointment.isFinalCheckDone()) {
            throw new PaymentException("請先完成進行中預約的核對（美容狀況備註 + 家長簽名）後才能結帳");
        }
        if (transactionRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new PaymentException("此預約已有交易紀錄");
        }
    }

    /**
     * 扣除加購零售商品的庫存。顧客結帳當下就把商品帶走，所以匯款待對帳也要扣；
     * 在寫入交易前執行，庫存不足時整筆結帳中止，不會留下已收款但庫存沒扣的狀態。
     */
    private void deductRetailStock(Long appointmentId) {
        for (AppointmentItem item : appointmentItemRepository.findByAppointmentId(appointmentId)) {
            if (item.getRetailProductId() != null) {
                retailProductService.deductStock(item.getRetailProductId(), 1);
            }
        }
    }

    /**
     * 計算實收金額（帳面合計另存於 baseAmount）。
     * 服務促銷（首次體驗、貓咪 90 天回洗）不限付款方式；會員等級折扣只有儲值金付款才有，
     * 且逐項判斷、與服務促銷擇優不疊加。儲值金付款僅限員工操作，並立即從錢包扣款。
     */
    private int chargeAmount(Appointment appointment, PaymentMethod method, boolean byStaff) {
        if (method != PaymentMethod.WALLET) {
            return method.calculateFinalAmount(calculateAmountWithRewashDiscount(appointment));
        }
        if (!byStaff) {
            throw new PaymentException("儲值金結帳僅限店家/員工於後台操作");
        }
        String member = appointment.getUser().getUsername();
        int amount = calculateWalletAmountPerItem(appointment, walletService.getWallet(member).getDiscount());
        walletService.deduct(member, amount, appointment.getId());
        return amount;
    }

    // ── 1x-1. 確認匯款收款：店員核對銀行入帳後，訂單才正式轉為已完成 ──────
    @Transactional
    public TransactionResponse confirmWireTransferPayment(Long appointmentId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可確認收款");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        Transaction transaction = transactionRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到此預約的交易紀錄"));

        if (transaction.getPaymentMethod() != PaymentMethod.WIRE_TRANSFER) {
            throw new PaymentException("此交易不是匯款付款，無需確認收款");
        }
        if (transaction.isPaid()) {
            throw new PaymentException("此筆匯款已確認收款過，請勿重複操作");
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
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!user.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作退款");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (!appointment.isPaid()) {
            throw new PaymentException("此預約尚未結帳，無法退款");
        }

        Transaction transaction = transactionRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到此預約的交易紀錄"));

        // 1. 若原本用儲值金付款，退回儲值餘額
        if (transaction.getPaymentMethod() == PaymentMethod.WALLET) {
            walletService.refund(
                    appointment.getUser().getUsername(),
                    transaction.getFinalAmount(),
                    appointmentId,
                    "預約 #" + appointmentId + " 退款");
        }

        // 2. 刪除這筆預約已經記過的所有績效積分（接進/完成/接出/服務項目）
        List<PerformanceRecord> records =
                performanceRecordRepository.findByAppointmentId(appointmentId);
        if (!records.isEmpty()) {
            performanceRecordRepository.deleteAll(records);
        }

        // 3. 刪除現場開單（依預約編號）已勾選的服務項目，讓店家可以重新開單
        List<AppointmentItem> items =
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

    // ── 需求（2026-09-24）：員工手機版結帳頁的金額預覽 ─────────────────────
    // 直接呼叫 checkout() 實際扣款用的同兩支計算方法，確保畫面上的預覽金額
    // 跟實際結帳金額一定一致，不另外在 controller 重算一份。純讀取，不寫入資料。

    /** 現金／LINE Pay／匯款：只套首次體驗、回洗優惠，不套會員折扣 */
    @Transactional
    public int previewStandardAmount(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        return PaymentMethod.CASH
                .calculateFinalAmount(calculateAmountWithRewashDiscount(appointment));
    }

    /** 儲值金：逐項目擇優套用首次體驗／回洗優惠／會員折扣 */
    @Transactional
    public int previewWalletAmount(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        double discount = walletService.getWallet(appointment.getUser().getUsername()).getDiscount();
        return calculateWalletAmountPerItem(appointment, discount);
    }

    // ── 結帳金額計算（擇優規則見 DiscountCalculator）──────────────────────

    /** 計價用的項目：優先用到店開單的實際項目；沒有的話退回顧客線上勾選的項目。 */
    private record PriceLine(double price, PerformanceCategory category, boolean memberDiscountEligible) {
    }

    private List<PriceLine> priceLines(Appointment appointment) {
        List<AppointmentItem> checkinItems = appointmentItemRepository.findByAppointmentId(appointment.getId());
        if (checkinItems.isEmpty()) {
            return appointment.getSelectedItems().stream()
                    .map(item -> new PriceLine(item.getPrice() != null ? item.getPrice() : 0,
                            item.getPerformanceCategory(), item.isDiscountEligible()))
                    .toList();
        }
        // 開單項目的會員折扣資格以服務項目目前的設定為準；零售商品、自訂項目（沒有對應服務項目）不打折，
        // 服務項目已被刪除時視為可打折
        Map<Long, Boolean> eligibleByItemId = groomingItemRepository.findAllById(checkinItems.stream()
                        .map(AppointmentItem::getGroomingItemId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(GroomingItem::getId, GroomingItem::isDiscountEligible));
        return checkinItems.stream()
                .map(item -> new PriceLine(item.getPrice(), item.getPerformanceCategory(),
                        item.getGroomingItemId() != null && eligibleByItemId.getOrDefault(item.getGroomingItemId(), true)))
                .toList();
    }

    /** 儲值金付款（需求 5、8-1）：逐項目在服務促銷與會員折扣之間擇優，不疊加。 */
    private int calculateWalletAmountPerItem(Appointment appointment, double memberDiscountRate) {
        EligibleOffers offers = discountCalculator.eligibleOffers(appointment);
        double total = priceLines(appointment).stream()
                .mapToDouble(line -> offers.resolve(line.price(), line.category(),
                        line.memberDiscountEligible(), memberDiscountRate).price())
                .sum();
        return (int) Math.round(total);
    }

    /** 非儲值金付款（需求 8-1）：只套用服務促銷；沒有任何促銷資格時就是預約金額。 */
    private int calculateAmountWithRewashDiscount(Appointment appointment) {
        EligibleOffers offers = discountCalculator.eligibleOffers(appointment);
        if (offers.isEmpty()) {
            return appointment.getTotalAmount();
        }
        double total = priceLines(appointment).stream()
                .mapToDouble(line -> offers.resolve(line.price(), line.category(), false, 1.0).price())
                .sum();
        return (int) Math.round(total);
    }

    /** 已付款的預約結帳交易（不含待確認的匯款）。 */
    public List<Transaction> findPaidTransactions() {
        return transactionRepository.findByPaidTrue();
    }
}
