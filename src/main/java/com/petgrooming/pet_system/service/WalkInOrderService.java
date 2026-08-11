package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.notification.LineMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 需求 5 / 6：現場開單 + 經手人（綁定員工帳號）+ 積分結算。
 *
 * 積分串接既有績效系統：經手人一旦被填上（開單當下直接填，或事後從待補清單補），
 * 就立刻依該項目快照的 performanceCategory / points 建立一筆 PerformanceRecord，
 * 跟預約結帳（PaymentService.autoCreatePerformanceRecords）走同一套績效表，
 * 會真正算進員工的月結算，而不是只在現場開單頁面自己的報表裡打轉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkInOrderService {

    private final WalkInOrderRepository orderRepository;
    private final WalkInOrderItemRepository orderItemRepository;
    private final GroomingItemRepository groomingItemRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PerformanceService performanceService;
    private final PerformanceRecordRepository performanceRecordRepository;
    private final LineMessagingService lineMessagingService;

    // ── 需求 5：建立現場單（存入交易紀錄）───────────────────────────────────
    @Transactional
    public WalkInOrderResponse create(WalkInOrderCreateRequest req, String createdByUsername) {

        WalkInOrder order = WalkInOrder.builder()
                .petName(req.getPetName())
                .note(req.getNote())
                .build();

        // 開單人姓名（快照）+ 帳號（供 CHECKIN 積分歸屬與之後查詢）
        User creator = userRepository.findByUsername(createdByUsername).orElse(null);
        if (creator != null) {
            order.setCreatedBy(creator.getName());
            order.setCreatedByStaff(creator);
        }

        // 關聯會員（選填）
        if (req.getMemberUsername() != null && !req.getMemberUsername().isBlank()) {
            User member = userRepository.findByUsername(req.getMemberUsername())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "找不到會員：" + req.getMemberUsername()));
            order.setMember(member);
        }

        // 逐項建立 OrderItem，快照名稱 / 價格 / 積分；若當下就指定經手人，順便建立績效紀錄
        int total = 0;
        for (WalkInOrderCreateRequest.Item line : req.getItems()) {
            GroomingItem gi = groomingItemRepository.findByItemCode(line.getItemCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "找不到項目代碼：" + line.getItemCode()));

            WalkInOrderItem item = WalkInOrderItem.builder()
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName())
                    .price((int) Math.round(gi.getPrice()))
                    .points(gi.getPoints())
                    .performanceCategory(gi.getPerformanceCategory())
                    .build();

            if (line.getOperatorStaffId() != null) {
                User staff = userRepository.findById(line.getOperatorStaffId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "找不到員工 #" + line.getOperatorStaffId()));
                item.setOperatorStaff(staff);
            }

            order.addItem(item);
            total += item.getPrice();
        }
        order.setTotalAmount(total);

        WalkInOrder saved = orderRepository.save(order);

        // 對開單當下就有指定經手人的項目，立刻寫入績效
        for (WalkInOrderItem item : saved.getItems()) {
            if (item.getOperatorStaff() != null) {
                awardPoints(item, saved);
            }
        }

        log.info("現場開單 #{} 完成，共 {} 項，總額 {}", saved.getId(), saved.getItems().size(), total);

        // 開單當下記入「接進」積分給開單人（比照預約單「開始服務」的邏輯，
        // 現場單沒有獨立的開始服務步驟，開單本身就是這個時間點）
        if (creator != null) {
            performanceService.addWalkInRecord(
                    creator.getId(),
                    saved.getId(),
                    PerformanceCategory.CHECKIN,
                    PerformanceCategory.CHECKIN.getDefaultPoints(),
                    saved.getCreatedAt().toLocalDate(),
                    "接待入場：現場單 #" + saved.getId());
        }

        return WalkInOrderResponse.from(saved);
    }

    // ── 結束服務：服務項目全部做完（比照預約單邏輯）─────────────────────
    // 狀態不影響 paid，只記錄「誰結束的」+ 完成積分，並強制之後的核對必須先做這一步。
    @Transactional
    public WalkInOrderResponse endService(Long orderId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("找不到現場單 #" + orderId));

        if (order.isPaid()) {
            throw new IllegalArgumentException("此單已結帳，無法結束服務");
        }
        if (order.isServiceEndedDone()) {
            throw new IllegalArgumentException("此單已結束服務，請勿重複操作");
        }

        order.setServiceEndedDone(true);
        order.setServiceEndedStaff(staff);
        order.setServiceEndedAt(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        performanceService.addWalkInRecord(
                staff.getId(),
                order.getId(),
                PerformanceCategory.COMPLETE,
                PerformanceCategory.COMPLETE.getDefaultPoints(),
                LocalDate.now(),
                "結束服務：現場單 #" + order.getId());

        // 若這張單有綁定會員，通知家長可以來接寵物了
        if (saved.getMember() != null) {
            String notifyText = String.format(
                    "【慕沐村 Mulage pet】您好，%s 的美容服務已經完成囉！%n隨時可以來店接毛孩回家 🐾",
                    saved.getPetName());
            lineMessagingService.pushText(saved.getMember().getLineUserId(), notifyText);
        }

        return WalkInOrderResponse.from(saved);
    }

    // ── 核對：填美容狀況備註 + 簽名確認（比照預約單邏輯）───────────────
    // 須先完成結束服務才能核對；核對完成才能結帳。
    @Transactional
    public WalkInOrderResponse finalCheck(Long orderId, String note, String signatureData, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("找不到現場單 #" + orderId));

        if (!order.isServiceEndedDone()) {
            throw new IllegalArgumentException("請先點擊「結束服務」才能進行核對");
        }
        if (order.isFinalCheckDone()) {
            throw new IllegalArgumentException("此單已完成核對，請勿重複操作");
        }

        String noteTrim = note == null ? "" : note.trim();
        if (noteTrim.isEmpty()) {
            throw new IllegalArgumentException("請填寫本次美容狀況備註");
        }
        String sigTrim = signatureData == null ? "" : signatureData.trim();
        if (sigTrim.isEmpty() || !sigTrim.startsWith("data:image") || sigTrim.length() < 1000) {
            throw new IllegalArgumentException("請請家長於簽名板完成簽名確認");
        }

        order.setFinalCheckDone(true);
        order.setFinalCheckStaff(staff);
        order.setFinalCheckNote(noteTrim);
        order.setFinalCheckSignatureImage(sigTrim);
        order.setFinalCheckAt(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        performanceService.addWalkInRecord(
                staff.getId(),
                order.getId(),
                PerformanceCategory.CHECKOUT,
                PerformanceCategory.CHECKOUT.getDefaultPoints(),
                LocalDate.now(),
                "接待送出核對：現場單 #" + order.getId());

        return WalkInOrderResponse.from(saved);
    }

    // ── 需求：現場單串接結帳 ─────────────────────────────────────────────
    // 選現金/信用卡/LinePay：純標記已付款（金流在店家手上完成，系統只留紀錄）。
    // 選儲值金：實際呼叫 WalletService.deduct() 扣款（走既有悲觀鎖，跟預約結帳同一套邏輯），
    // 僅限「有綁定會員」的單才能用（非會員沒有錢包可扣）。
    @Transactional
    public WalkInOrderResponse checkout(Long orderId,
            com.petgrooming.pet_system.enums.PaymentMethod paymentMethod,
            String staffUsername) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("找不到現場單 #" + orderId));

        if (order.isPaid()) {
            throw new IllegalArgumentException("此單已完成結帳");
        }
        if (!order.isFinalCheckDone()) {
            throw new IllegalArgumentException("請先完成「結束服務」與「核對」後才能結帳");
        }

        if (paymentMethod == com.petgrooming.pet_system.enums.PaymentMethod.WALLET) {
            if (order.getMember() == null) {
                throw new IllegalArgumentException("此單無會員資料，無法用儲值金付款");
            }
            walletService.deduct(
                    order.getMember().getUsername(),
                    order.getTotalAmount(),
                    null,
                    "現場單 #" + order.getId() + " 消費扣款");
        }

        order.setPaid(true);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentTime(java.time.LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        log.info("現場單 #{} 結帳完成，付款方式：{}", saved.getId(), paymentMethod);
        return WalkInOrderResponse.from(saved);
    }

    // ── 退款：僅限「已結帳」的現場單 ──────────────────────────────────
    // 退款後直接刪除這筆現場單（含底下所有項目），積分全部刪除、
    // 儲值金付款的補回餘額，之後店家重新開一張全新的單即可。
    @Transactional
    public void refund(Long orderId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!user.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作退款");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("找不到現場單 #" + orderId));

        if (!order.isPaid()) {
            throw new IllegalArgumentException("此單尚未結帳，無法退款");
        }

        // 1. 若原本用儲值金付款，退回會員儲值餘額
        if (order.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WALLET
                && order.getMember() != null) {
            walletService.refund(
                    order.getMember().getUsername(),
                    order.getTotalAmount(),
                    null,
                    "現場單 #" + orderId + " 退款");
        }

        // 2. 刪除這張單已經記過的所有績效積分（接進/完成/接出/服務項目）
        List<com.petgrooming.pet_system.model.PerformanceRecord> records =
                performanceRecordRepository.findByWalkInOrderId(orderId);
        if (!records.isEmpty()) {
            performanceRecordRepository.deleteAll(records);
        }

        // 3. 直接刪除整筆現場單（items 設定了 cascade + orphanRemoval，會一併刪除）
        orderRepository.delete(order);

        log.info("現場單 #{} 已退款並刪除，操作人：{}", orderId, username);
    }

    // ── 所有現場單（交易紀錄列表）───────────────────────────────────────────
    public List<WalkInOrderResponse> listAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(WalkInOrderResponse::from).toList();
    }

    // ── 查單筆現場單完整明細（供會員信息頁「消費記錄」點擊查看用）─────────
    public WalkInOrderResponse getById(Long id) {
        WalkInOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到現場單 #" + id));
        return WalkInOrderResponse.from(order);
    }

    // ── 需求 6：待補經手人清單（operatorStaff 為 null 的項目）──────────────
    public List<WalkInOrderResponse.ItemLine> pendingOperatorItems() {
        List<WalkInOrderItem> pending = orderItemRepository.findByOperatorStaffIsNull();
        List<WalkInOrderResponse.ItemLine> result = new ArrayList<>();
        for (WalkInOrderItem oi : pending) {
            WalkInOrderResponse.ItemLine line = new WalkInOrderResponse.ItemLine();
            line.setItemId(oi.getId());
            line.setGroomingItemId(oi.getGroomingItemId());
            line.setItemName(oi.getItemName());
            line.setPrice(oi.getPrice());
            line.setPoints(oi.getPoints());
            line.setOperatorFilled(false);
            result.add(line);
        }
        return result;
    }

    // ── 需求 6：補填某個項目的經手人（同步寫入績效紀錄）─────────────────────
    @Transactional
    public void fillOperator(Long orderItemId, Long staffId) {
        WalkInOrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到項目 #" + orderItemId));

        if (item.getOperatorStaff() != null) {
            throw new IllegalArgumentException("此項目已填寫經手人，無法重複填寫");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工 #" + staffId));

        item.setOperatorStaff(staff);
        orderItemRepository.save(item);

        awardPoints(item, item.getOrder());
        log.info("項目 #{} 補填經手人：{}", orderItemId, staff.getName());
    }

    // ── 積分寫入：一筆項目只會被計入一次（pointsAwarded 防重複）────────────
    private void awardPoints(WalkInOrderItem item, WalkInOrder order) {
        if (item.isPointsAwarded())
            return; // 防呆：避免同一筆被重複計分
        if (item.getPerformanceCategory() == PerformanceCategory.OTHER)
            return; // 跟預約結帳同樣規則，OTHER 不計分
        if (item.getPoints() <= 0)
            return;

        LocalDate serviceDate = order.getCreatedAt() != null
                ? order.getCreatedAt().toLocalDate()
                : LocalDate.now();

        performanceService.addWalkInRecord(
                item.getOperatorStaff().getId(),
                order.getId(),
                item.getPerformanceCategory(),
                item.getPoints(),
                serviceDate,
                "現場單 #" + order.getId() + " - " + item.getItemName());

        item.setPointsAwarded(true);
        orderItemRepository.save(item);
    }

    // ── 需求 6：經手人積分結算報表（排除未填寫）────────────────────────────
    public List<OperatorPointsResponse> operatorPointsReport() {
        List<Object[]> rows = orderItemRepository.sumPointsByOperator();
        List<OperatorPointsResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            String operatorName = (String) r[1];
            double totalPoints = r[2] != null ? ((Number) r[2]).doubleValue() : 0;
            int totalAmount = r[3] != null ? ((Number) r[3]).intValue() : 0;
            long itemCount = r[4] != null ? ((Number) r[4]).longValue() : 0;
            result.add(new OperatorPointsResponse(operatorName, totalPoints, totalAmount, itemCount));
        }
        return result;
    }
}
