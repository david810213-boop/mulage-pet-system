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
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.UserRepository;
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
        int baseAmount  = appointment.getTotalAmount();
        int finalAmount = req.getPaymentMethod().calculateFinalAmount(baseAmount);

        // 1e-1. 若選擇「儲值金」付款：
        //   - 僅限店家/員工於後台操作（顧客不可自行使用此付款方式）
        //   - 套用會員等級折扣（現金/信用卡/LinePay 不套用會員折扣）
        //   - 折扣後金額才是實際扣款與交易紀錄的最終金額
        if (req.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WALLET) {
            if (!isStaffOrAdmin) {
                throw new IllegalArgumentException("儲值金結帳僅限店家/員工於後台操作");
            }
            double discount = walletService.getWallet(appointment.getUser().getUsername()).getDiscount();
            finalAmount = (int) Math.round(finalAmount * discount);
            walletService.deduct(appointment.getUser().getUsername(), finalAmount, appointment.getId());
        }

        // 1f. 決定經手人
        String handledBy = isOwner
                ? "會員自助（" + user.getName() + "）"
                : "員工：" + user.getName();

        // 1g. 建立交易紀錄
        Transaction transaction = Transaction.builder()
                .appointment(appointment)
                .user(appointment.getUser())
                .paymentMethod(req.getPaymentMethod())
                .baseAmount(baseAmount)
                .finalAmount(finalAmount)
                .paid(true)
                .paymentTime(LocalDateTime.now())
                .handledBy(handledBy)
                .build();

        transactionRepository.save(transaction);

        // 1h. 將預約標記為已付款，並把狀態轉為「已完成」（結帳＝整筆服務結束）
        appointment.setPaid(true);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        // 1i. 自動建立績效紀錄（依預約選擇的服務項目 + 負責員工）
        autoCreatePerformanceRecords(appointment, user, isStaffOrAdmin);

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

        // 自動補一筆「完成」積分（整筆預約完成，記入結帳經手人的「完成確認」）
        User completeStaff = appointment.getStaff();
        if (completeStaff == null && isStaffOrAdmin) {
            completeStaff = checkoutUser;
        }
        if (completeStaff == null) {
            log.warn("預約 #{} 無法判斷經手人，跳過「完成」積分", appointment.getId());
            return;
        }
        performanceService.addRecord(
                completeStaff.getId(),
                appointment.getId(),
                PerformanceCategory.COMPLETE,
                PerformanceCategory.COMPLETE.getDefaultPoints(),
                appointment.getDate(),
                "預約 #" + appointment.getId() + " 完成"
        );

        log.info("預約 #{} 結帳後自動建立績效紀錄（經手人：{}）",
                appointment.getId(), completeStaff.getName());
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
}