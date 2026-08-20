package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.NotificationService;
import com.petgrooming.pet_system.notification.LineMessagingService;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final com.petgrooming.pet_system.repository.TransactionRepository transactionRepository; // 消費明細用
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final GroomingItemRepository groomingItemRepository; // ⚡ 1. 補上這行注入，用來撈取服務價格
    private final NotificationService notificationService;
    private final LineMessagingService lineMessagingService;
    private final SlotCapacityService slotCapacityService; // 需求 3：時段名額控管
    private final ClosedDateService closedDateService; // 需求 16：公休日設定
    private final PetGroomingNoteRepository petGroomingNoteRepository; // 進行中核對：毛孩美容狀況歷史
    private final PerformanceService performanceService; // 進行中核對：接待送出積分
    private final com.petgrooming.pet_system.repository.AppointmentItemRepository appointmentItemRepository; // 現場開單（依預約編號）
    private final CatRewashDiscountService catRewashDiscountService; // 需求 8-1：貓咪 90 天回洗優惠
    private final WalletService walletService; // 需求 8-1：消費明細顯示實際套用的折扣種類需要會員折扣率
    private final RetailProductService retailProductService; // 需求（追加）：預約結帳頁加購零售商品

    private static final LocalTime OPENING = LocalTime.of(11, 0);
    private static final LocalTime CLOSING = LocalTime.of(19, 0);
    private static final int SLOT_HOURS = 2;
    private static final int SLOT_CAPACITY = 5; // 同時段最多 5 隻
    private static final LocalTime EARLY_SLOT_CUTOFF = LocalTime.of(11, 0); // 需求 13-3：隔天預約早於此時間要額外提醒準時到場

    @Transactional
    public AppointmentResponse book(AppointmentRequest req, String username) {

        // 1a. 確認使用者存在
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        // 1b. 確認 petId 對應的寵物存在
        Pet pet = petRepository.findById(req.getPetId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到寵物，請先至「我的寵物」新增後再預約"));

        // 1c. 確認這隻寵物屬於此 user（不能預約別人的寵物）
        if (!pet.getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "找不到寵物，請先至「我的寵物」新增後再預約");
        }

        // 1c-2. 需求 16：公休日不開放預約（後端強制擋，避免前端被繞過或代客預約誤排）
        if (closedDateService.isClosed(req.getDate())) {
            throw new IllegalArgumentException("該日為公休日，恕不開放預約，請選擇其他日期");
        }

        // 1d. 驗證時間在營業時間內
        if (req.getStartTime().isBefore(OPENING) || req.getEndTime().isAfter(CLOSING)) {
            throw new IllegalArgumentException(
                    "超出營業時間！請輸入 " + OPENING + " – " + CLOSING + " 之間的時間");
        }

        // 1e. 確認結束時間在開始時間之後
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new IllegalArgumentException("結束時間必須晚於開始時間");
        }

        // 1e-2. 定型化契約：必須有實際簽名圖片（非空白畫布）
        String signatureData = req.getContractSignatureData() == null ? "" : req.getContractSignatureData().trim();
        // 空白 canvas 匯出的 dataURL 長度很短（通常僅一兩百字元），實際簽名筆劃會讓資料明顯變長
        if (signatureData.isEmpty() || !signatureData.startsWith("data:image")
                || signatureData.length() < 1000) {
            throw new IllegalArgumentException("請詳閱定型化契約，並在簽名板上親筆簽名後再送出預約");
        }

        // 1f. 需求 3：同時段最多 5 隻（併發安全）。
        // 先確保計數列存在（獨立交易），再於本交易內加悲觀鎖 +1；額滿則丟出例外。
        slotCapacityService.ensureSlot(req.getDate(), req.getStartTime());
        try {
            slotCapacityService.reserve(req.getDate(), req.getStartTime());
        } catch (IllegalStateException e) {
            // 轉成 IllegalArgumentException 讓 Controller 統一回 400
            throw new IllegalArgumentException(e.getMessage());
        }

        // ⚡ 2. 防呆安全鎖：萬一前端完全沒傳任何服務項目，直接攔截不往下跑
        if (req.getSelectedItems() == null || req.getSelectedItems().isEmpty()) {
            throw new IllegalArgumentException("請至少選擇一項美容服務項目！");
        }

        // ⚡ 3. 核心校正：將前端傳來的 List<String> 服務代碼，轉換為資料庫中的真實實體物件清單

        List<GroomingItem> actualItems = req.getSelectedItems().stream()
                .map((String itemCode) -> groomingItemRepository.findByItemCode(itemCode)
                        .orElseThrow(() -> new IllegalArgumentException("找不到有效的服務項目代碼：" + itemCode)))
                .filter(item -> !item.isDeleted())
                .toList();

        // ⚡ 4. 動態計算總金額
        int total = (int) actualItems.stream()
                .mapToDouble(GroomingItem::getPrice) // 轉為 double 計算
                .sum();

        // 1h. 建立並儲存 Appointment
        // 需求 13：當天臨時預約（date == 今天）不用等店家手動點確認——
        // 反正待確認的緩衝時間本來就是留給「提早幾天」的預約做行程安排用，
        // 當天才排進來的單子等於已經是要馬上進行的，直接視為已確認並立即推播通知。
        boolean isSameDayBooking = req.getDate().isEqual(LocalDate.now());
        Appointment appointment = Appointment.builder()
                .user(user)
                .petName(pet.getName())
                .petType(pet.getPetType().name())
                .date(req.getDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .selectedItems(actualItems)
                .totalAmount(total)
                .paid(false)
                // 需求 3：非當天的預約，顧客送出後為「待確認」，等店家敲定最後時間
                // 需求 13：當天臨時預約直接視為已確認
                .status(isSameDayBooking ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING_CONFIRM)
                .confirmedTime(isSameDayBooking ? java.time.LocalDateTime.now() : null)
                .contractSignatureImage(signatureData)
                .contractAgreedAt(java.time.LocalDateTime.now())
                .build();

        // 若有指派員工，設入（選填）
        if (req.getStaffId() != null) {
            userRepository.findById(req.getStaffId()).ifPresent(appointment::setStaff);
        }

        Appointment saved = appointmentRepository.save(appointment);

        // 1i. 發送通知（原本的舊版模擬通知，實際上只印 console log，非真正 LINE 推播，先保留不動）
        notificationService.sendBookingConfirmation(
                user.getUsername(), pet.getName(), req.getDate(), req.getStartTime());
        notificationService.scheduleReminder(
                user.getUsername(), req.getDate(), req.getStartTime());

        // 需求 13：當天臨時預約已直接視為已確認，立即發送真正的 LINE 確認通知
        // （非當天的預約要等店家在後台點「確認預約」，由 confirm() 方法發送）
        if (isSameDayBooking) {
            sendConfirmedNotify(saved);
        }

        return AppointmentResponse.from(saved);
    }

    // ── 取消預約 ──────────────────────────────────────────────────────────
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, CancelAppointmentRequest req, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        boolean isOwner = appointment.getUser().getId().equals(user.getId());
        boolean isStaffOrAdmin = user.isStaffOrAdmin();

        if (!isOwner && !isStaffOrAdmin) {
            throw new IllegalArgumentException("權限不足：只能取消自己的預約");
        }

        if (appointment.isCancelled()) {
            throw new IllegalArgumentException("此預約已經是取消狀態");
        }

        // 已結帳的預約需先走退款流程，暫不開放直接取消，避免金流/績效資料不一致
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已完成結帳，無法直接取消，請先處理退款");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason(req != null ? req.getReason() : null);
        appointment.setCancelledBy(isOwner
                ? "會員自行取消（" + user.getName() + "）"
                : "員工取消：" + user.getName());

        // 需求 3：釋放該時段名額，讓其他人可以遞補
        slotCapacityService.release(appointment.getDate(), appointment.getStartTime());

        Appointment saved = appointmentRepository.save(appointment);

        // 通知顧客預約已取消（目前為系統 log 模擬，之後可接真實推播）
        System.out.println("[系統通知] 預約 #" + appointment.getId() + "（" + appointment.getPetName()
                + "，" + appointment.getDate() + " " + appointment.getStartTime()
                + "）已取消。取消人：" + appointment.getCancelledBy());

        return AppointmentResponse.from(saved);
    }

    // ── 查詢自己的預約 ────────────────────────────────────────────────────
    public List<AppointmentResponse> getMyAppointments(String username) {
        return appointmentRepository.findByUserUsername(username)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    // ── 查詢所有預約（STAFF/ADMIN）────────────────────────────────────────
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll()
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    // ── 店家後台查詢所有預約（含內部備注，需求 7）──────────────────────────
    public List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> getAllForAdmin() {
        return appointmentRepository.findAll()
                .stream()
                .map(a -> {
                    var res = com.petgrooming.pet_system.dto.AppointmentAdminResponse.from(a);
                    // 需求 10：查這筆預約有沒有「待對帳」的匯款交易（已建立交易但尚未確認收款）
                    transactionRepository.findByAppointmentId(a.getId()).ifPresent(tx -> {
                        if (tx.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WIRE_TRANSFER
                                && !tx.isPaid()) {
                            res.setPendingWireTransfer(true);
                            res.setPendingTransactionId(tx.getId());
                        }
                    });
                    return res;
                })
                .toList();
    }

    // ── 店家設定預約備注（需求 7：雙可見性）────────────────────────────────
    // 只更新有帶值的欄位；internalNote 僅後台可見，memberNote 會員可見。
    @Transactional
    public com.petgrooming.pet_system.dto.AppointmentAdminResponse setNotes(
            Long appointmentId, String internalNote, String memberNote, String username) {

        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可編輯備注");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (internalNote != null)
            appointment.setInternalNote(internalNote);
        if (memberNote != null)
            appointment.setMemberNote(memberNote);

        Appointment saved = appointmentRepository.save(appointment);
        return com.petgrooming.pet_system.dto.AppointmentAdminResponse.from(saved);
    }

    // ── 查詢可預約時段（需求 3：回傳每個時段剩餘名額，上限 5）──────────────
    public List<TimeSlotResponse> getAvailableSlots(LocalDate date) {
        List<TimeSlotResponse> allSlots = new ArrayList<>();

        // 需求 16：公休日當天不開放任何時段，直接回傳空清單
        // （不動 SlotCapacity 底下各時段的 capacity 數字，公休日設定解除後名額設定自動還原）
        if (closedDateService.isClosed(date)) {
            return allSlots;
        }

        LocalTime current = OPENING;
        while (current.isBefore(CLOSING)) {
            LocalTime next = current.plusHours(SLOT_HOURS);
            if (next.isAfter(CLOSING))
                next = CLOSING;

            int booked = slotCapacityService.bookedCount(date, current);
            int capacity = slotCapacityService.getCapacity(date, current);
            int remaining = Math.max(0, capacity - booked);
            allSlots.add(new TimeSlotResponse(
                    current, next, remaining > 0, booked, capacity, remaining));

            current = next;
        }
        return allSlots;
    }

    // ── 店家確認預約並敲定最後時間（需求 3）────────────────────────────────
    // confirmedTime 由店家傳入（可與原申請時間不同）；狀態轉為 CONFIRMED。
    @Transactional
    public AppointmentResponse confirm(Long appointmentId, LocalDateTime confirmedTime, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可確認預約");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (appointment.isCancelled()) {
            throw new IllegalArgumentException("此預約已取消，無法確認");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedTime(confirmedTime != null ? confirmedTime : LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        // 需求 3 / 13：用官方 LINE 通知會員「預約已確認」
        sendConfirmedNotify(saved);

        return AppointmentResponse.from(saved);
    }

    // 需求 13：預約確認通知文字內容 + 發送，供「店家點確認」與「當日臨時預約自動確認」共用
    private void sendConfirmedNotify(Appointment saved) {
        // 因為每隻寵物的美容所需時間會依體型/毛長/性情而不同，這裡不承諾一個確切完成時間，
        // 而是請會員留意店家後續來電或訊息通知的預計完成時間。
        String notifyText = String.format(
                "【慕沐村 Mulage pet】您好，%s 的美容預約已確認！%n" +
                        "由於每隻毛孩的施作時間會依體型、毛況及個性而有所不同，" +
                        "我們會在施作過程中致電或傳訊息通知您預計完成時間，請留意來電或訊息喔 🐾",
                saved.getPetName());
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);
    }

    // ── 需求 13：每天 19:00 自動掃描「隔天」預約，發送前一日提醒 ─────────────
    // 只提醒狀態已是 CONFIRMED 的預約（PENDING_CONFIRM 代表店家還沒確認最後時間，
    // 不適合先跟會員說「明天請準時到場」）。
    // 當天現場開單／臨時預約因為在 book() 建立當下就已經直接送出確認通知（見 sendConfirmedNotify
    // 於 isSameDayBooking 分支的呼叫），日期本來就不會等於「明天」，天然不會被這支排程重複提醒到。
    // reminderSent 旗標避免同一筆預約被排程重複發送（例如當天系統重啟造成 cron 觸發兩次）。
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Taipei")
    @Transactional
    public void sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> targets = appointmentRepository
                .findByDateAndReminderSentFalseAndStatus(tomorrow, AppointmentStatus.CONFIRMED);

        if (targets.isEmpty()) {
            log.info("[需求13-前日提醒] {} 沒有待提醒的預約", tomorrow);
            return;
        }

        for (Appointment appointment : targets) {
            String reminderText = buildReminderText(appointment);
            lineMessagingService.pushText(appointment.getUser().getLineUserId(), reminderText);
            appointment.setReminderSent(true);
            appointmentRepository.save(appointment);
        }
        log.info("[需求13-前日提醒] 已處理 {} 筆 {} 的預約提醒", targets.size(), tomorrow);
    }

    // 需求 13-3：隔天預約時間在上午 11:00 前的，提醒訊息額外加註提醒準時到場
    private String buildReminderText(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "【慕沐村 Mulage pet】提醒您，%s 明天（%s）%s 有預約寵物美容服務喔！",
                appointment.getPetName(),
                appointment.getDate(),
                appointment.getStartTime()));
        if (appointment.getStartTime().isBefore(EARLY_SLOT_CUTOFF)) {
            sb.append(" 請準時到場，歡迎提前15分鐘抵達 🐾");
        }
        return sb.toString();
    }

    // ── 店員開始服務：CONFIRMED → IN_PROGRESS（寵物已到店開始施作）──────────
    @Transactional
    public AppointmentResponse startProgress(Long appointmentId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (appointment.isCancelled()) {
            throw new IllegalArgumentException("此預約已取消，無法開始服務");
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("僅「已確認」的預約可開始服務");
        }
        if (!appointment.isCheckinOrderConfirmed()) {
            throw new IllegalArgumentException("請先依現場情況開立服務項目訂單，才能開始服務");
        }

        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        Appointment saved = appointmentRepository.save(appointment);

        // 該店員記入「接待入場」積分（沿用既有 CHECKIN 積分類別）
        performanceService.addRecord(
                staff.getId(),
                appointment.getId(),
                PerformanceCategory.CHECKIN,
                PerformanceCategory.CHECKIN.getDefaultPoints(),
                appointment.getDate(),
                "接待入場：預約 #" + appointment.getId());

        return AppointmentResponse.from(saved);
    }

    // ── 結束服務：服務項目全部做完，通知家長來店接寵物 ──────────────────────
    // 狀態維持「進行中」不變，只記錄「誰結束的」+ 完成積分，並強制要求
    // 之後的核對（finalCheck）必須先完成這一步才能進行。
    @Transactional
    public AppointmentResponse endService(Long appointmentId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("僅「進行中」的預約可結束服務");
        }
        if (appointment.isServiceEndedDone()) {
            throw new IllegalArgumentException("此預約已結束服務，請勿重複操作");
        }

        appointment.setServiceEndedDone(true);
        appointment.setServiceEndedStaff(staff);
        appointment.setServiceEndedAt(LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        // 誰點結束服務，「完成」積分就記給誰（原本掛在結帳時發放，現在改到這一步）
        performanceService.addRecord(
                staff.getId(),
                appointment.getId(),
                PerformanceCategory.COMPLETE,
                PerformanceCategory.COMPLETE.getDefaultPoints(),
                appointment.getDate(),
                "結束服務：預約 #" + appointment.getId());

        // 用官方 LINE 通知家長可以來店接寵物了
        String notifyText = String.format(
                "【慕沐村 Mulage pet】您好，%s 的美容服務已經完成囉！%n" +
                        "隨時可以來店接毛孩回家 🐾",
                saved.getPetName());
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);

        return AppointmentResponse.from(saved);
    }

    // ── 現場開單（依預約編號）：家長到店後，店員依現場情況確認/調整服務項目 ──
    // 確認後才能開始服務；項目可先不指定經手人，之後從待補清單補上。
    @Transactional
    public AppointmentResponse confirmCheckinOrder(Long appointmentId, List<String> itemCodes, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (appointment.isCancelled()) {
            throw new IllegalArgumentException("此預約已取消，無法開單");
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("僅「已確認」的預約可開立現場服務項目訂單");
        }
        if (appointment.isCheckinOrderConfirmed()) {
            throw new IllegalArgumentException("此預約已開立過現場服務項目訂單，請至「補項目經手人」調整");
        }
        if (itemCodes == null || itemCodes.isEmpty()) {
            throw new IllegalArgumentException("請至少選擇一項服務項目");
        }

        int total = 0;
        for (String code : itemCodes) {
            GroomingItem gi = groomingItemRepository.findByItemCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("找不到項目代碼：" + code));

            com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName())
                    .price((int) Math.round(gi.getPrice()))
                    .points(gi.getPoints())
                    .performanceCategory(gi.getPerformanceCategory())
                    .build();
            appointmentItemRepository.save(item);
            total += item.getPrice();
        }

        appointment.setTotalAmount(total);
        appointment.setCheckinOrderConfirmed(true);
        Appointment saved = appointmentRepository.save(appointment);

        return AppointmentResponse.from(saved);
    }

    // ── 需求（追加）：預約結帳頁加購零售商品 ────────────────────────────
    // 結帳前都可以加購（不受核對/checkinOrderConfirmed 限制，零售商品不是美容服務，
    // 走的是不同的完成判定邏輯）；quantity 幾件就建幾筆項目列，沿用 WalkInOrderItem
    // 「一列 = 一份，price 是單價」的慣例，不加 quantity 欄位，降低牽連風險。
    @Transactional
    public void addRetailItem(Long appointmentId, Long retailProductId, int quantity, String username) {
        if (quantity <= 0) throw new IllegalArgumentException("加購數量必須大於 0");

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已結帳，無法再加購商品");
        }

        var product = retailProductService.getById(retailProductId);

        int addedTotal = 0;
        for (int i = 0; i < quantity; i++) {
            com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .retailProductId(product.getId())
                    .itemName(product.getName())
                    .price(product.getPrice())
                    .points(0.0)
                    .performanceCategory(PerformanceCategory.OTHER)
                    .build();
            appointmentItemRepository.save(item);
            addedTotal += product.getPrice();
        }
        appointment.setTotalAmount(appointment.getTotalAmount() + addedTotal);
        appointmentRepository.save(appointment);

        log.info("預約 #{} 加購商品「{}」x{}", appointmentId, product.getName(), quantity);
    }

    // ── 需求（追加）：編輯訂單——結帳前新增一筆美容服務項目 ────────────────
    // 跟加購零售商品同一套「結帳前才准動」的限制；核對時發現漏開/開錯項目，
    // 不用整筆退款重開，直接在這裡補上即可。
    @Transactional
    public void addGroomingItem(Long appointmentId, Long groomingItemId, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }

        GroomingItem gi = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到服務項目"));

        com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                .builder()
                .appointment(appointment)
                .groomingItemId(gi.getId())
                .itemName(gi.getName())
                .price((int) Math.round(gi.getPrice()))
                .points(gi.getPoints())
                .performanceCategory(gi.getPerformanceCategory())
                .build();
        appointmentItemRepository.save(item);

        appointment.setTotalAmount(appointment.getTotalAmount() + item.getPrice());
        appointment.setCheckinOrderConfirmed(true); // 保險起見一併標記，避免極少數尚未開過單的情況卡在後續流程
        appointmentRepository.save(appointment);

        log.info("預約 #{} 編輯新增服務項目「{}」", appointmentId, gi.getName());
    }

    // 移除一筆項目（結帳前都可以移除，不管是零售商品還是美容服務項目；
    // 結帳後只能整筆退款重開，這裡的「已結帳」防呆維持不變）。
    @Transactional
    public void removeItem(Long appointmentId, Long itemId, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }

        com.petgrooming.pet_system.model.AppointmentItem item = appointmentItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到項目 #" + itemId));
        if (!item.getAppointment().getId().equals(appointmentId)) {
            throw new IllegalArgumentException("項目不屬於這筆預約");
        }
        if (appointmentItemRepository.findByAppointmentId(appointmentId).size() <= 1) {
            throw new IllegalArgumentException("這是最後一筆項目，無法移除；如果整筆都要取消，請改用退款流程");
        }

        appointment.setTotalAmount(appointment.getTotalAmount() - item.getPrice());
        appointmentItemRepository.delete(item);
        appointmentRepository.save(appointment);
    }

    // ── 待補經手人清單（現場開單項目中 operatorStaff 為 null 的）─────────────
    // 需求（追加）：只列出「有積分可算」的項目——零售商品加購（points 固定 0）
    // 不會產生績效，補了經手人也沒有計算意義，不需要出現在這份待辦清單裡。
    public List<com.petgrooming.pet_system.dto.AppointmentItemResponse> pendingItemOperators() {
        return appointmentItemRepository.findByOperatorStaffIsNull().stream()
                .filter(item -> item.getPoints() > 0)
                .map(com.petgrooming.pet_system.dto.AppointmentItemResponse::from)
                .toList();
    }

    // ── 補填某個現場開單項目的經手人（同步寫入績效紀錄）─────────────────────
    @Transactional
    public void fillItemOperator(Long appointmentItemId, Long staffId) {
        com.petgrooming.pet_system.model.AppointmentItem item = appointmentItemRepository.findById(appointmentItemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到項目 #" + appointmentItemId));

        if (item.getOperatorStaff() != null) {
            throw new IllegalArgumentException("此項目已填寫經手人，無法重複填寫");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工 #" + staffId));

        item.setOperatorStaff(staff);
        appointmentItemRepository.save(item);

        awardItemPoints(item);
    }

    // ── 現場開單項目積分寫入：一筆項目只會被計入一次（pointsAwarded 防重複）──
    public void awardItemPoints(com.petgrooming.pet_system.model.AppointmentItem item) {
        if (item.isPointsAwarded())
            return;
        if (item.getOperatorStaff() == null)
            return;
        if (item.getPerformanceCategory() == PerformanceCategory.OTHER)
            return;
        if (item.getPoints() <= 0)
            return;

        Appointment appt = item.getAppointment();
        performanceService.addRecord(
                item.getOperatorStaff().getId(),
                appt.getId(),
                item.getPerformanceCategory(),
                item.getPoints(),
                appt.getDate(),
                "預約 #" + appt.getId() + " - " + item.getItemName());

        item.setPointsAwarded(true);
        appointmentItemRepository.save(item);
    }

    // ── 結帳時呼叫：把此預約現場開單項目中「已有經手人」的積分一次結算 ──────
    // 沒有經手人的項目會跳過，之後從「補項目經手人」清單補上時再另外觸發積分。
    public boolean hasCheckinOrderItems(Long appointmentId) {
        return !appointmentItemRepository.findByAppointmentId(appointmentId).isEmpty();
    }

    @Transactional
    public void awardPendingItemPointsForAppointment(Long appointmentId) {
        List<com.petgrooming.pet_system.model.AppointmentItem> items = appointmentItemRepository
                .findByAppointmentId(appointmentId);
        for (com.petgrooming.pet_system.model.AppointmentItem item : items) {
            awardItemPoints(item);
        }
    }

    // ── 進行中核對（接待送出）───────────────────────────────────────────
    // 店員從「進行中」的預約選擇核對：記錄本次美容狀況備註（同步存入毛孩歷史），
    // 並取得家長現場簽名確認；完成後才可進入結帳，且將該店員記為「接待送出」積分。
    @Transactional
    public AppointmentResponse finalCheck(Long appointmentId, FinalCheckRequest req, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("僅「進行中」的預約可進行核對");
        }
        if (!appointment.isServiceEndedDone()) {
            throw new IllegalArgumentException("請先點擊「結束服務」，通知家長來店後才能進行核對");
        }
        if (appointment.isFinalCheckDone()) {
            throw new IllegalArgumentException("此預約已完成核對，請勿重複操作");
        }

        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (note.isEmpty()) {
            throw new IllegalArgumentException("請填寫本次毛孩美容狀況備註");
        }

        String signatureData = req.getSignatureData() == null ? "" : req.getSignatureData().trim();
        if (signatureData.isEmpty() || !signatureData.startsWith("data:image") || signatureData.length() < 1000) {
            throw new IllegalArgumentException("請請家長於簽名板完成簽名確認");
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 寫入本次核對紀錄到預約本身
        appointment.setFinalCheckDone(true);
        appointment.setFinalCheckStaff(staff);
        appointment.setFinalCheckNote(note);
        appointment.setFinalCheckSignatureImage(signatureData);
        appointment.setFinalCheckAt(now);
        Appointment saved = appointmentRepository.save(appointment);

        // 2. 同步存入毛孩美容狀況歷史，供店家日後查詢客製化美容參考
        Pet pet = petRepository.findByOwnerUsernameAndName(
                appointment.getUser().getUsername(), appointment.getPetName()).orElse(null);
        if (pet != null) {
            PetGroomingNote historyNote = PetGroomingNote.builder()
                    .pet(pet)
                    .appointmentId(appointment.getId())
                    .staff(staff)
                    .note(note)
                    .serviceDate(appointment.getDate())
                    .build();
            petGroomingNoteRepository.save(historyNote);
        }

        // 3. 該店員記入「接待送出」積分（沿用既有 CHECKOUT 積分類別）
        performanceService.addRecord(
                staff.getId(),
                appointment.getId(),
                PerformanceCategory.CHECKOUT,
                PerformanceCategory.CHECKOUT.getDefaultPoints(),
                appointment.getDate(),
                "接待送出核對：預約 #" + appointment.getId());

        return AppointmentResponse.from(saved);
    }

    // ── 取得某預約的現場開單項目明細（供進行中核對頁面顯示給家長核對用）─────
    public List<com.petgrooming.pet_system.model.AppointmentItem> getCheckinItems(Long appointmentId) {
        return appointmentItemRepository.findByAppointmentId(appointmentId);
    }

    // ── 2. 取得使用者的寵物清單（預約表單下拉選單用）──────────────────
    public List<Pet> getMyPetsForBooking(String username) {
        return petRepository.findByOwnerUsername(username);

    }

    // ── 取得某筆預約的完整消費明細（供 LIFF「我的預約」點擊查看用）──────
    // 權限：本人（該預約的顧客）或店家/員工皆可查看
    public com.petgrooming.pet_system.dto.AppointmentDetailResponse getAppointmentDetail(
            Long appointmentId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));

        boolean isOwner = appointment.getUser().getId().equals(user.getId());
        if (!isOwner && !user.isStaffOrAdmin()) {
            throw new IllegalArgumentException("權限不足：只能查看自己的預約明細");
        }

        // 需求 8-1 修正：先查出這筆預約是否有交易紀錄、用什麼付款方式，
        // 才能判斷每個項目「實際」套用的是回洗優惠還是會員折扣（兩者只能擇一）。
        var transactionOpt = transactionRepository.findByAppointmentId(appointmentId);
        boolean paidByWallet = transactionOpt
                .map(tx -> tx.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WALLET)
                .orElse(false);
        double memberDiscountRate = paidByWallet
                ? walletService.getWallet(appointment.getUser().getUsername()).getDiscount()
                : 1.0;
        boolean rewashEligible = catRewashDiscountService.isRewashEligible(appointment); // 需求 8-1

        List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems = appointmentItemRepository
                .findByAppointmentId(appointmentId);

        List<com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem> items;
        if (!checkinItems.isEmpty()) {
            items = checkinItems.stream()
                    .map(ci -> {
                        boolean memberEligible = ci.getGroomingItemId() != null
                                && groomingItemRepository.findById(ci.getGroomingItemId())
                                        .map(com.petgrooming.pet_system.model.GroomingItem::isDiscountEligible)
                                        .orElse(true);
                        boolean rewashApplicable = rewashEligible
                                && catRewashDiscountService.isCatBathCategory(ci.getPerformanceCategory());
                        return com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                                .itemId(ci.getId())
                                .name(ci.getItemName())
                                .price(ci.getPrice())
                                .operatorName(ci.getOperatorStaff() != null ? ci.getOperatorStaff().getName() : null)
                                .discountEligible(memberEligible)
                                .rewashEligible(rewashApplicable)
                                .retailItem(ci.getRetailProductId() != null)
                                .appliedDiscountType(transactionOpt.isPresent()
                                        ? resolveAppliedDiscountType(rewashApplicable, memberEligible && paidByWallet, memberDiscountRate)
                                        : null)
                                .build();
                    })
                    .toList();
        } else {
            items = appointment.getSelectedItems().stream()
                    .map(gi -> {
                        boolean rewashApplicable = rewashEligible && catRewashDiscountService.isCatBathItem(gi);
                        return com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                                .name(gi.getName())
                                .price((int) Math.round(gi.getPrice()))
                                .operatorName(null)
                                .discountEligible(gi.isDiscountEligible())
                                .rewashEligible(rewashApplicable)
                                .appliedDiscountType(transactionOpt.isPresent()
                                        ? resolveAppliedDiscountType(rewashApplicable, gi.isDiscountEligible() && paidByWallet, memberDiscountRate)
                                        : null)
                                .build();
                    })
                    .toList();
        }

        var detailBuilder = com.petgrooming.pet_system.dto.AppointmentDetailResponse.builder()
                .appointmentCode(String.format("AP%03d", appointment.getId()))
                .petName(appointment.getPetName())
                .petType(appointment.getPetType())
                .date(appointment.getDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .statusLabel(appointment.getStatus().getLabel())
                .cancelled(appointment.isCancelled())
                .memberNote(appointment.getMemberNote())
                .items(items)
                .totalAmount(appointment.getTotalAmount())
                .paid(appointment.isPaid());

        transactionOpt.ifPresent(tx -> {
            detailBuilder
                    .paymentMethodLabel(tx.getPaymentMethod() != null ? tx.getPaymentMethod().getDisplayName() : null);
            detailBuilder.paymentTime(tx.getPaymentTime());
            detailBuilder.handledBy(tx.getHandledBy());
            // 需求 5：實際扣款金額獨立顯示，不要覆蓋掉帳面未打折的 totalAmount，
            // 兩者一起顯示消費者才看得出「有沒有打折、打了多少」。
            detailBuilder.chargedAmount(tx.getFinalAmount());
        });

        return detailBuilder.build();
    }

    // 需求 8-1 修正：只回傳「實際套用哪一種折扣」的標籤，不重算金額
    // （金額計算仍以 PaymentService.checkout() 當下算出、存入 Transaction.finalAmount 的為準，
    // 這裡只是為了消費明細顯示用途、用同一套「擇一」規則反推標籤）。
    private com.petgrooming.pet_system.enums.DiscountType resolveAppliedDiscountType(
            boolean rewashApplicable, boolean memberApplicable, double memberDiscountRate) {
        return catRewashDiscountService
                .resolvePreferredDiscount(100.0, rewashApplicable, memberApplicable, memberDiscountRate)
                .type();
    }
}