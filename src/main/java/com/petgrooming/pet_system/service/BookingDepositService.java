package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.DepositAppointmentView;
import com.petgrooming.pet_system.dto.DepositPaymentRequest;
import com.petgrooming.pet_system.dto.DepositPaymentView;
import com.petgrooming.pet_system.dto.DepositPreviewResponse;
import com.petgrooming.pet_system.dto.DepositStatusResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.DepositPaymentStatus;
import com.petgrooming.pet_system.enums.DepositRefundStatus;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.BankAccountInfo;
import com.petgrooming.pet_system.model.DepositPayment;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.DepositPaymentRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 預約訂金：份額計算、顧客回報匯款、店家人工核帳、逾期自動取消、取消退款、免收。
 *
 * 訂金以「同一會員同一天的有效預約」為一組計算，每筆預約記自己的份額（depositShare）與已收金額（depositPaid）。
 * 需要補訂金的新預約為「待付訂金」，付清後當天需要訂金的預約一律轉為已確認。規則見 {@link DepositPolicy}，
 * 設計取捨見 docs/adr/0009-booking-deposit.md。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingDepositService {

    private final AppointmentRepository appointmentRepository;
    private final DepositPaymentRepository depositPaymentRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PetRepository petRepository;
    private final PetConsumptionHistoryService petConsumptionHistoryService;
    private final SlotCapacityService slotCapacityService;
    private final StoreSettingsService storeSettingsService;
    private final GoogleCalendarService googleCalendarService;
    private final AppointmentNotificationService notificationService;

    // ── 建立預約 ────────────────────────────────────────────────────────

    /**
     * 預約寫入後（同一個交易內）重新計算當天訂金。還有未付清的金額時，這筆新預約改為待付訂金並設定付款期限；
     * 當天原本的預約維持原狀態，它們新增的份額由這筆新預約的付款一起收。
     *
     * @return 當天尚未付清的訂金（0＝不需要付）
     */
    @Transactional
    public int applyAfterBooking(Appointment saved, Pet pet, boolean returningPet) {
        userRepository.lockById(saved.getUser().getId());
        saved.setDepositLargeFirstVisit(!returningPet && DepositPolicy.isLargeDog(pet.getPetType(), pet.getWeight()));
        List<Appointment> group = recalculate(saved.getUser().getId(), saved.getDate());
        int outstanding = outstanding(group);
        if (outstanding > 0) {
            LocalDateTime now = LocalDateTime.now();
            saved.setStatus(AppointmentStatus.AWAITING_DEPOSIT);
            saved.setConfirmedTime(null);
            saved.setDepositDueAt(DepositPolicy.paymentDueAt(now, startOf(saved)));
        }
        appointmentRepository.saveAll(group);
        return outstanding;
    }

    /** 送出預約前試算需要再付的訂金（不寫入任何資料）。 */
    @Transactional(readOnly = true)
    public DepositPreviewResponse preview(String username, Long petId, LocalDate date) {
        User user = getUser(username);
        if (date == null || petId == null) {
            return new DepositPreviewResponse(0, false, null);
        }
        if (hasStoredValue(user.getId())) {
            return new DepositPreviewResponse(0, true, "儲值金會員免付訂金");
        }
        Pet pet = petRepository.findById(petId)
                .filter(p -> p.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到寵物"));
        boolean returning = petConsumptionHistoryService.hasPriorPaidService(user.getId(), pet.getName(), null);
        boolean largeFirst = !returning && DepositPolicy.isLargeDog(pet.getPetType(), pet.getWeight());

        List<Appointment> group = activeGroup(user.getId(), date);
        List<Boolean> flags = new ArrayList<>(group.stream().map(Appointment::isDepositLargeFirstVisit).toList());
        flags.add(largeFirst);
        boolean waived = group.stream().anyMatch(Appointment::isDepositWaived);
        List<Integer> shares = DepositPolicy.shares(flags, waived);
        int required = 0;
        int paid = 0;
        for (int i = 0; i < group.size(); i++) {
            if (!group.get(i).isPaid()) {
                required += shares.get(i);
                paid += Math.min(shares.get(i), group.get(i).getDepositPaid());
            }
        }
        required += shares.get(shares.size() - 1);
        int amount = Math.max(0, required - paid);
        String reason = amount == 0 ? null
                : largeFirst && flags.size() == 1 ? "首次體驗的大型犬需預付訂金"
                : "同一天預約 " + flags.size() + " 位需預付訂金";
        return new DepositPreviewResponse(amount, false, reason);
    }

    // ── 顧客回報匯款 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DepositStatusResponse status(String username, LocalDate date) {
        return statusFor(getUser(username), date);
    }

    @Transactional
    public DepositStatusResponse submitPayment(String username, DepositPaymentRequest req) {
        User user = getUser(username);
        userRepository.lockById(user.getId());
        List<Appointment> group = lockedGroup(user.getId(), req.date());
        int outstanding = outstanding(group);
        if (outstanding <= 0) {
            throw new AppointmentException("這一天沒有需要支付的訂金");
        }
        boolean pending = depositPaymentRepository
                .findByUserIdAndAppointmentDateOrderByCreatedAtDesc(user.getId(), req.date()).stream()
                .anyMatch(p -> p.getStatus() == DepositPaymentStatus.PENDING);
        if (pending) {
            throw new AppointmentException("已收到您的匯款資訊，店家核帳中，請稍候");
        }
        DepositPayment payment = depositPaymentRepository.save(DepositPayment.builder()
                .user(user)
                .appointmentDate(req.date())
                .amount(outstanding)
                .lastFiveDigits(req.lastFiveDigits())
                .remitNote(blankToNull(req.remitNote()))
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[訂金] 會員 #{} 回報 {} 的訂金匯款 ${}（#{}）", user.getId(), req.date(), outstanding, payment.getId());
        notificationService.notifyStaffDepositSubmitted(payment);
        return statusFor(user, req.date());
    }

    // ── 店家核帳 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DepositPaymentView> pendingPayments() {
        return depositPaymentRepository.findWithUserByStatus(DepositPaymentStatus.PENDING).stream()
                .map(p -> new DepositPaymentView(p.getId(), p.getUser().getId(), p.getUser().getName(),
                        p.getAppointmentDate(), p.getAmount(), p.getLastFiveDigits(), p.getRemitNote(),
                        p.getCreatedAt(), outstanding(activeGroup(p.getUser().getId(), p.getAppointmentDate()))))
                .toList();
    }

    /** 待核帳的訂金匯款筆數（手機版今日頁統計用，只做 COUNT）。 */
    @Transactional(readOnly = true)
    public long countPendingPayments() {
        return depositPaymentRepository.countByStatus(DepositPaymentStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<DepositAppointmentView> awaitingAppointments() {
        return appointmentRepository.findWithUserByStatus(AppointmentStatus.AWAITING_DEPOSIT).stream()
                .map(BookingDepositService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<DepositAppointmentView> pendingRefunds() {
        return appointmentRepository.findWithUserByDepositRefundStatus(DepositRefundStatus.PENDING).stream()
                .map(BookingDepositService::view).toList();
    }

    /**
     * 確認收到匯款：金額依序補到當天份額還沒付滿的預約，多出來的記在第一筆預約、結帳時折抵。
     * 付清後，當天需要訂金、還在待付訂金或待確認的預約一律轉為已確認並通知顧客。
     */
    @Transactional
    public DepositPaymentView confirmPayment(Long paymentId, String staffName) {
        DepositPayment payment = lockPendingPayment(paymentId);
        Long userId = payment.getUser().getId();
        userRepository.lockById(userId);
        List<Appointment> group = recalculate(userId, payment.getAppointmentDate());
        if (group.stream().noneMatch(a -> !a.isPaid())) {
            throw new AppointmentException("這一天已經沒有有效的預約（可能已逾期取消），"
                    + "請改為駁回，並將款項退還顧客");
        }
        allocate(group, payment.getAmount());
        payment.setStatus(DepositPaymentStatus.CONFIRMED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(staffName);
        depositPaymentRepository.save(payment);

        if (outstanding(group) == 0) {
            confirmGroup(group);
        }
        appointmentRepository.saveAll(group);
        log.info("[訂金] 匯款 #{} 已入帳 ${}（{}）", paymentId, payment.getAmount(), staffName);
        return new DepositPaymentView(payment.getId(), userId, payment.getUser().getName(),
                payment.getAppointmentDate(), payment.getAmount(), payment.getLastFiveDigits(),
                payment.getRemitNote(), payment.getCreatedAt(), outstanding(group));
    }

    @Transactional
    public void rejectPayment(Long paymentId, String staffName, String reason) {
        DepositPayment payment = lockPendingPayment(paymentId);
        payment.setStatus(DepositPaymentStatus.REJECTED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(staffName);
        payment.setRejectReason(blankToNull(reason));
        depositPaymentRepository.save(payment);
        notificationService.sendDepositRejected(payment);
    }

    /** 店員免收這一天的訂金（原因由呼叫端記入操作紀錄）。已收的訂金保留到結帳折抵。 */
    @Transactional
    public int waive(Long userId, LocalDate date) {
        userRepository.lockById(userId);
        List<Appointment> group = lockedGroup(userId, date);
        if (group.isEmpty()) {
            throw new AppointmentException("這一天沒有有效的預約");
        }
        group.forEach(a -> a.setDepositWaived(true));
        recalculate(group);
        int confirmed = confirmGroup(group);
        appointmentRepository.saveAll(group);
        return confirmed;
    }

    /** 店員匯款退還訂金給顧客後標記完成。 */
    @Transactional
    public Appointment markRefunded(Long appointmentId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (a.getDepositRefundStatus() != DepositRefundStatus.PENDING) {
            throw new AppointmentException("這筆預約沒有待退款的訂金");
        }
        a.setDepositRefundStatus(DepositRefundStatus.DONE);
        a.setDepositRefundedAt(LocalDateTime.now());
        return appointmentRepository.save(a);
    }

    // ── 取消、改期、結帳（由 AppointmentService／PaymentService 呼叫）───────

    /** 預約明細頁提示店員：現在取消的話，保留的訂金退多少。 */
    @Transactional(readOnly = true)
    public DepositPolicy.Refund quoteCancel(Long appointmentId, boolean storeCause) {
        return appointmentRepository.findById(appointmentId)
                .map(a -> quoteCancel(a, storeCause))
                .orElse(new DepositPolicy.Refund(0, 0));
    }

    /** 取消當下保留的訂金可以退多少。 */
    public DepositPolicy.Refund quoteCancel(Appointment a, boolean storeCause) {
        return DepositPolicy.refundOnCancel(a.depositCredit(), startOf(a), LocalDateTime.now(), storeCause);
    }

    /**
     * 預約已設為取消後呼叫：依取消時間計算保留訂金的退款與沒收，再重新計算當天其他預約的份額。
     * 其他待付訂金的預約若因此不再需要訂金，回到待確認。
     */
    @Transactional
    public void onCancelled(Appointment cancelled, boolean storeCause) {
        userRepository.lockById(cancelled.getUser().getId());
        DepositPolicy.Refund refund = quoteCancel(cancelled, storeCause);
        if (refund.refund() + refund.forfeited() > 0) {
            cancelled.setDepositRefundAmount(cancelled.getDepositRefundAmount() + refund.refund());
            cancelled.setDepositForfeitedAmount(cancelled.getDepositForfeitedAmount() + refund.forfeited());
            if (refund.refund() > 0) {
                cancelled.setDepositRefundStatus(DepositRefundStatus.PENDING);
            }
            log.info("[訂金] 預約 #{} 取消：退 ${}、不退 ${}", cancelled.getId(), refund.refund(), refund.forfeited());
        }
        cancelled.setDepositShare(0);
        cancelled.setDepositDueAt(null);
        appointmentRepository.save(cancelled);
        settleGroupAfterChange(cancelled.getUser().getId(), cancelled.getDate());
    }

    /** 改期後重新計算原日期與新日期的份額（訂金跟著預約走）。 */
    @Transactional
    public void onRescheduled(Appointment moved, LocalDate fromDate) {
        userRepository.lockById(moved.getUser().getId());
        settleGroupAfterChange(moved.getUser().getId(), fromDate);
        List<Appointment> target = recalculate(moved.getUser().getId(), moved.getDate());
        appointmentRepository.saveAll(target);
    }

    /**
     * 結帳時折抵保留的訂金：回傳折抵金額；訂金多於應付金額時，差額轉為待退款。
     */
    public int applyAtCheckout(Appointment a, int amountDue) {
        int credit = a.depositCredit();
        if (credit <= 0) {
            return 0;
        }
        int applied = Math.min(credit, Math.max(0, amountDue));
        a.setDepositApplied(a.getDepositApplied() + applied);
        int excess = credit - applied;
        if (excess > 0) {
            a.setDepositRefundAmount(a.getDepositRefundAmount() + excess);
            a.setDepositRefundStatus(DepositRefundStatus.PENDING);
        }
        return applied;
    }

    /** 預約目前保留、結帳時可以折抵的訂金。 */
    @Transactional(readOnly = true)
    public int creditFor(Long appointmentId) {
        return appointmentRepository.findById(appointmentId).map(Appointment::depositCredit).orElse(0);
    }

    // ── 逾期未付：每 10 分鐘掃描 ──────────────────────────────────────────

    /**
     * 付款期限已過、還是待付訂金的預約自動取消並釋放時段。顧客在期限內已回報匯款、店家還沒核帳的不取消，
     * 留給店家核帳後決定。
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Taipei")
    @Transactional
    public void expireOverdue() {
        LocalDateTime now = LocalDateTime.now();
        for (Appointment a : appointmentRepository.findWithUserByStatus(AppointmentStatus.AWAITING_DEPOSIT)) {
            if (a.getDepositDueAt() == null || a.getDepositDueAt().isAfter(now) || hasPaymentBefore(a)) {
                continue;
            }
            a.setStatus(AppointmentStatus.CANCELLED);
            a.setCancelledAt(now);
            a.setCancelReason("未於期限內支付訂金");
            a.setCancelledBy("系統：逾期未付訂金");
            slotCapacityService.release(a.getDate(), a.getStartTime());
            googleCalendarService.deleteEvent(a);
            onCancelled(a, false);
            notificationService.sendDepositExpired(a);
            log.info("[訂金] 預約 #{} 逾期未付訂金，已自動取消", a.getId());
        }
    }

    // ── 內部計算 ────────────────────────────────────────────────────────

    private List<Appointment> activeGroup(Long userId, LocalDate date) {
        return appointmentRepository.findByUserIdAndDateAndStatusNotOrderByIdAsc(
                userId, date, AppointmentStatus.CANCELLED);
    }

    /**
     * 要改寫份額或狀態時用加鎖讀取：MySQL 可重複讀下，一般查詢看到的是交易開始時的快照，
     * 同一會員同時送出兩筆預約時會漏算對方剛提交的那一筆。
     */
    private List<Appointment> lockedGroup(Long userId, LocalDate date) {
        return appointmentRepository.lockByUserIdAndDateAndStatusNot(userId, date, AppointmentStatus.CANCELLED);
    }

    private List<Appointment> recalculate(Long userId, LocalDate date) {
        List<Appointment> group = lockedGroup(userId, date);
        recalculate(group);
        return group;
    }

    /** 依目前的一組預約重新寫入每筆份額。整天任一筆被免收、或會員有儲值金時都不收。 */
    private void recalculate(List<Appointment> group) {
        if (group.isEmpty()) {
            return;
        }
        boolean exempt = group.stream().anyMatch(Appointment::isDepositWaived)
                || hasStoredValue(group.get(0).getUser().getId());
        List<Integer> shares = DepositPolicy.shares(
                group.stream().map(Appointment::isDepositLargeFirstVisit).toList(), exempt);
        for (int i = 0; i < group.size(); i++) {
            group.get(i).setDepositShare(shares.get(i));
        }
    }

    /** 尚未付清的金額：已結帳的預約不再計入。 */
    static int outstanding(List<Appointment> group) {
        return group.stream().filter(a -> !a.isPaid())
                .mapToInt(a -> Math.max(0, a.getDepositShare() - a.getDepositPaid())).sum();
    }

    private static void allocate(List<Appointment> group, int amount) {
        int remaining = amount;
        for (Appointment a : group) {
            if (a.isPaid() || remaining <= 0) {
                continue;
            }
            int shortfall = Math.max(0, a.getDepositShare() - a.getDepositPaid());
            int add = Math.min(shortfall, remaining);
            a.setDepositPaid(a.getDepositPaid() + add);
            remaining -= add;
        }
        int extra = remaining;
        if (extra > 0) {
            group.stream().filter(a -> !a.isPaid()).findFirst()
                    .ifPresent(a -> a.setDepositPaid(a.getDepositPaid() + extra));
        }
    }

    /** 付清（或免收）後：當天需要訂金、還沒確認的預約轉為已確認並通知顧客，回傳轉換的筆數。 */
    private int confirmGroup(List<Appointment> group) {
        int count = 0;
        for (Appointment a : group) {
            boolean needsConfirm = a.isAwaitingDeposit()
                    || (a.getStatus() == AppointmentStatus.PENDING_CONFIRM && a.getDepositShare() > 0);
            if (!needsConfirm) {
                continue;
            }
            a.setStatus(AppointmentStatus.CONFIRMED);
            a.setConfirmedTime(LocalDateTime.now());
            a.setDepositDueAt(null);
            notificationService.sendConfirmedNotify(a);
            googleCalendarService.syncEvent(a);
            count++;
        }
        return count;
    }

    /** 當天預約有變動（取消、改期離開）後重新計算；不再欠訂金時，待付訂金的預約回到待確認。 */
    private void settleGroupAfterChange(Long userId, LocalDate date) {
        List<Appointment> group = recalculate(userId, date);
        if (outstanding(group) == 0) {
            for (Appointment a : group) {
                if (a.isAwaitingDeposit()) {
                    a.setStatus(AppointmentStatus.PENDING_CONFIRM);
                    a.setDepositDueAt(null);
                }
            }
        }
        appointmentRepository.saveAll(group);
    }

    private boolean hasPaymentBefore(Appointment a) {
        return depositPaymentRepository
                .findByUserIdAndAppointmentDateOrderByCreatedAtDesc(a.getUser().getId(), a.getDate()).stream()
                .anyMatch(p -> p.getStatus() == DepositPaymentStatus.PENDING
                        && !p.getCreatedAt().isAfter(a.getDepositDueAt()));
    }

    private boolean hasStoredValue(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(w -> w.getBalance() != null && w.getBalance() > 0)
                .orElse(false);
    }

    private DepositPayment lockPendingPayment(Long paymentId) {
        String status = depositPaymentRepository.lockStatusById(paymentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到這筆訂金匯款"));
        if (!DepositPaymentStatus.PENDING.name().equals(status)) {
            throw new AppointmentException("這筆訂金匯款已經處理過了");
        }
        return depositPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到這筆訂金匯款"));
    }

    private DepositStatusResponse statusFor(User user, LocalDate date) {
        List<Appointment> group = activeGroup(user.getId(), date);
        int required = group.stream().filter(a -> !a.isPaid()).mapToInt(Appointment::getDepositShare).sum();
        int paid = group.stream().filter(a -> !a.isPaid()).mapToInt(Appointment::getDepositPaid).sum();
        LocalDateTime dueAt = group.stream().filter(Appointment::isAwaitingDeposit)
                .map(Appointment::getDepositDueAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        List<DepositPayment> payments = depositPaymentRepository
                .findByUserIdAndAppointmentDateOrderByCreatedAtDesc(user.getId(), date);
        DepositPayment pending = payments.stream()
                .filter(p -> p.getStatus() == DepositPaymentStatus.PENDING).findFirst().orElse(null);
        String lastReject = payments.isEmpty() || payments.get(0).getStatus() != DepositPaymentStatus.REJECTED
                ? null : payments.get(0).getRejectReason();
        BankAccountInfo bank = storeSettingsService.getBankAccountInfo(BankAccountPurpose.CHECKOUT);
        return new DepositStatusResponse(date, required, paid, outstanding(group), dueAt,
                pending != null, pending == null ? 0 : pending.getAmount(), lastReject,
                bank == null ? null : bank.getBankName(),
                bank == null ? null : bank.getAccountNumber(),
                bank == null ? null : bank.getAccountHolder(),
                bank == null ? null : bank.getQrCodeUrl());
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
    }

    static LocalDateTime startOf(Appointment a) {
        return a.getDate().atTime(a.getStartTime());
    }

    private static DepositAppointmentView view(Appointment a) {
        return new DepositAppointmentView(a.getId(), String.format("AP%03d", a.getId()),
                a.getUser().getId(), a.getUser().getName(), a.getPetName(), a.getDate(), a.getStartTime(),
                a.getDepositShare(), a.getDepositPaid(), a.getDepositDueAt(),
                a.getDepositRefundAmount(), a.getDepositForfeitedAmount(), a.getCancelledBy());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
