package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.discount.DiscountCalculator;
import com.petgrooming.pet_system.service.discount.EligibleOffers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 現場單的結帳、匯款確認、退款與金額計算（折扣擇優規則見 DiscountCalculator），以及消費明細的折扣標示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkInPaymentService {

    private final WalkInOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PerformanceRecordRepository performanceRecordRepository;
    private final RetailProductService retailProductService; // 需求 7-1：結帳時扣零售商品庫存
    private final DiscountCalculator discountCalculator; // 首次體驗／回洗優惠與會員折扣擇優

    // ── 結帳 ─────────────────────────────────────────────────────────────
    // 選現金/信用卡/LinePay：純標記已付款（金流在店家手上完成，系統只留紀錄）。
    // 選儲值金：實際呼叫 WalletService.deduct() 扣款（走既有悲觀鎖，跟預約結帳同一套邏輯），
    // 僅限「有綁定會員」的單才能用（非會員沒有錢包可扣）。
    @Transactional
    public WalkInOrderResponse checkout(Long orderId,
            PaymentMethod paymentMethod,
            String staffUsername) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));

        if (order.isPaid()) {
            throw new PaymentException("此單已完成結帳");
        }
        // 需求 7-1 修正：純零售商品訂單（沒有任何美容服務項目）不需要「結束服務」「核對」這兩步——
        // 那兩步是針對美容服務設計的（核對還要填美容狀況備註+簽名），單純買東西沒有這些內容可填。
        boolean hasServiceItems = order.getItems().stream().anyMatch(i -> i.getGroomingItemId() != null);
        if (hasServiceItems && !order.isFinalCheckDone()) {
            throw new PaymentException("請先完成「結束服務」與「核對」後才能結帳");
        }

        // 需求 7-1：結帳成功才真的扣零售商品庫存（不管付款方式是不是匯款待對帳，
        // 因為顧客結完帳當下商品就會實際帶走，不是等匯款核對完成才拿走）。
        // 這裡故意在真正寫入 paid/chargedAmount 之前先做，扣庫存失敗（例如庫存被其他單搶先扣完）
        // 就直接整筆結帳拋例外中止，不會留下「已扣款但庫存沒扣成功」的不一致狀態。
        for (WalkInOrderItem item : order.getItems()) {
            if (item.getRetailProductId() != null) {
                retailProductService.deductStock(item.getRetailProductId(), 1);
            }
        }

        int chargedAmount = order.getTotalAmount();
        if (paymentMethod == PaymentMethod.WALLET) {
            if (order.getMember() == null) {
                throw new PaymentException("此單無會員資料，無法用儲值金付款");
            }
            // 需求 5：套用會員等級折扣，且逐項目判斷是否可享折扣（洗澡/剪毛/調理類打折，
            // 剪指甲/局部修剪/除廢毛等加購項目維持原價）。
            // 需求 8 修正：貓咪回洗優惠（若有會員綁定）與會員儲值折扣只能擇一，不疊加。
            double discount = walletService.getWallet(order.getMember().getUsername()).getDiscount();
            chargedAmount = calculateWalletAmountPerItem(order, discount);
            walletService.deduct(
                    order.getMember().getUsername(),
                    chargedAmount,
                    null,
                    "現場單 #" + order.getId() + " 消費扣款");
        } else {
            // 需求 8 修正：非儲值金付款（現金/LinePay/匯款）也要套用貓咪回洗優惠，
            // 跟預約結帳（PaymentService）邏輯一致——回洗優惠不限付款方式，只有會員折扣才限儲值金。
            chargedAmount = calculateAmountWithRewashDiscount(order);
        }

        // 需求 15 修正：匯款比照預約結帳（需求10）的「待對帳」機制——
        // 先記下付款方式與金額，但不標記已付款，等店家另外按「確認收款」才轉為已完成。
        // WalkInOrderItem 的積分是在指定經手人當下就發放（不是結帳才發放），
        // 所以這裡不用擔心「待對帳期間積分該不該先發」的問題，跟預約結帳的積分時機完全不同。
        boolean isWireTransfer = paymentMethod == PaymentMethod.WIRE_TRANSFER;

        order.setPaymentMethod(paymentMethod);
        order.setChargedAmount(chargedAmount);
        if (isWireTransfer) {
            WalkInOrder saved = orderRepository.save(order);
            log.info("現場單 #{} 選擇匯款結帳，進入待對帳狀態，等待店家確認收款", saved.getId());
            WalkInOrderResponse res = WalkInOrderResponse.from(saved);
            populateDiscountInfo(res, saved);
            return res;
        }

        order.setPaid(true);
        order.setPaymentTime(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        log.info("現場單 #{} 結帳完成，付款方式：{}", saved.getId(), paymentMethod);
        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        populateDiscountInfo(res, saved);
        return res;
    }

    // ── 確認匯款收款：店員核對銀行入帳後，現場單才正式轉為已完成 ─────────
    // 需求 15 修正：比照 PaymentService.confirmWireTransferPayment() 同一套邏輯。
    @Transactional
    public WalkInOrderResponse confirmWireTransferPayment(Long orderId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可確認收款");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));

        if (order.getPaymentMethod() != PaymentMethod.WIRE_TRANSFER) {
            throw new PaymentException("此單不是匯款付款，無需確認收款");
        }
        if (order.isPaid()) {
            throw new PaymentException("此單已確認收款過，請勿重複操作");
        }

        order.setPaid(true);
        order.setPaymentTime(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        log.info("現場單 #{} 匯款收款已確認，操作人：{}", orderId, username);
        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        populateDiscountInfo(res, saved);
        return res;
    }

    // ── 退款：僅限「已結帳」的現場單 ──────────────────────────────────
    // 退款後直接刪除這筆現場單（含底下所有項目），積分全部刪除、
    // 儲值金付款的補回餘額，之後店家重新開一張全新的單即可。
    @Transactional
    public void refund(Long orderId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!user.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作退款");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));

        if (!order.isPaid()) {
            throw new PaymentException("此單尚未結帳，無法退款");
        }

        // 1. 若原本用儲值金付款，退回會員儲值餘額（退實際扣款金額，不是帳面未打折總額）
        if (order.getPaymentMethod() == PaymentMethod.WALLET
                && order.getMember() != null) {
            int refundAmount = order.getChargedAmount() != null ? order.getChargedAmount() : order.getTotalAmount();
            walletService.refund(
                    order.getMember().getUsername(),
                    refundAmount,
                    null,
                    "現場單 #" + orderId + " 退款");
        }

        // 2. 刪除這張單已經記過的所有績效積分（接進/完成/接出/服務項目）
        List<PerformanceRecord> records =
                performanceRecordRepository.findByWalkInOrderId(orderId);
        if (!records.isEmpty()) {
            performanceRecordRepository.deleteAll(records);
        }

        // 3. 直接刪除整筆現場單（items 設定了 cascade + orphanRemoval，會一併刪除）
        orderRepository.delete(order);

        log.info("現場單 #{} 已退款並刪除，操作人：{}", orderId, username);
    }

    // 需求 8 修正：消費明細要能看出每個項目實際套用的是回洗優惠還是會員折扣，
    // 邏輯比照 AppointmentDetailService.getAppointmentDetail()：只有已結帳的單才反推得出「實際」套用哪一種，
    // 未結帳（尚未決定付款方式）則只標示 rewashEligible，不填 appliedDiscountType。
    // 需求（追加）：狗狗首次體驗優惠比照同樣模式，跟回洗優惠是互斥品種、不會同一個項目都符合。
    void populateDiscountInfo(WalkInOrderResponse res, WalkInOrder order) {
        EligibleOffers offers = discountCalculator.eligibleOffers(order);
        boolean paidByWallet = order.isPaid()
                && order.getPaymentMethod() == PaymentMethod.WALLET;
        double memberDiscountRate = paidByWallet
                ? walletService.getWallet(order.getMember().getUsername()).getDiscount()
                : 1.0;

        List<WalkInOrderItem> entities = order.getItems();
        List<WalkInOrderResponse.ItemLine> lines = res.getItems();
        for (int i = 0; i < entities.size() && i < lines.size(); i++) {
            WalkInOrderItem entity = entities.get(i);
            WalkInOrderResponse.ItemLine line = lines.get(i);
            PerformanceCategory category = entity.getPerformanceCategory();
            line.setRewashEligible(offers.applies(DiscountType.REWASH, category));
            // 狗狗、貓咪首次體驗共用同一個欄位顯示（前端只在意「是不是首次體驗」）
            line.setFirstVisitEligible(offers.applies(DiscountType.FIRST_VISIT, category));
            if (order.isPaid()) {
                line.setAppliedDiscountType(offers.resolve(entity.getPrice(), category,
                        entity.isDiscountEligible() && paidByWallet, memberDiscountRate).type());
            }
        }
    }

    // ── 需求（2026-09-24）：員工手機版現場單結帳頁的金額預覽 ────────────────
    // 跟 PaymentService 的同名方法同樣做法：直接呼叫 checkout() 實際扣款用的
    // 兩支計算方法，確保預覽金額跟實際結帳金額一致。純讀取，不寫入資料。

    /** 現金／LINE Pay／匯款：只套首次體驗、回洗優惠，不套會員折扣 */
    @Transactional
    public int previewStandardAmount(Long orderId) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        return calculateAmountWithRewashDiscount(order);
    }

    /** 儲值金：逐項目擇優套用首次體驗／回洗優惠／會員折扣；沒綁會員回傳 null（不能用儲值金） */
    @Transactional
    public Integer previewWalletAmount(Long orderId) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.getMember() == null) {
            return null;
        }
        double discount = walletService.getWallet(order.getMember().getUsername()).getDiscount();
        return calculateWalletAmountPerItem(order, discount);
    }

    // ── 現場單結帳金額計算（擇優規則見 DiscountCalculator）──────────────────

    /**
     * 儲值金付款（需求 5、8）：逐項目在服務促銷與會員折扣之間擇優。會員折扣資格用開單當下存的
     * discountEligible 快照，避免項目後來改設定，回頭影響到已經開好的舊單。
     */
    private int calculateWalletAmountPerItem(WalkInOrder order, double discount) {
        EligibleOffers offers = discountCalculator.eligibleOffers(order);
        double total = order.getItems().stream()
                .mapToDouble(item -> offers.resolve(item.getPrice(), item.getPerformanceCategory(),
                        item.isDiscountEligible(), discount).price())
                .sum();
        return (int) Math.round(total);
    }

    /** 非儲值金付款（需求 8）：只套用服務促銷；沒有任何促銷資格時就是開單金額。 */
    private int calculateAmountWithRewashDiscount(WalkInOrder order) {
        EligibleOffers offers = discountCalculator.eligibleOffers(order);
        if (offers.isEmpty()) {
            return order.getTotalAmount();
        }
        double total = order.getItems().stream()
                .mapToDouble(item -> offers.resolve(item.getPrice(), item.getPerformanceCategory(), false, 1.0).price())
                .sum();
        return (int) Math.round(total);
    }
}
