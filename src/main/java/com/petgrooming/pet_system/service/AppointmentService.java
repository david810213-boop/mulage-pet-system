package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
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
    private final GoogleCalendarService googleCalendarService; // 需求（追加）：Google 日曆串接
    private final SlotCapacityService slotCapacityService; // 需求 3：時段名額控管
    private final ClosedDateService closedDateService; // 需求 16：公休日設定
    private final PetGroomingNoteRepository petGroomingNoteRepository; // 進行中核對：毛孩美容狀況歷史
    private final PerformanceService performanceService; // 進行中核對：接待送出積分
    private final com.petgrooming.pet_system.repository.AppointmentItemRepository appointmentItemRepository; // 現場開單（依預約編號）
    private final CatRewashDiscountService catRewashDiscountService; // 需求 8-1：貓咪 90 天回洗優惠
    private final DogFirstVisitDiscountService dogFirstVisitDiscountService; // 需求（追加）：狗狗首次體驗優惠
    private final CatFirstVisitDiscountService catFirstVisitDiscountService; // 需求（追加）：貓咪首次體驗優惠（取代初體驗價目表）
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 需求（追加）：僅限既有客戶項目判斷
    private final com.petgrooming.pet_system.repository.GroomingItemComponentRepository groomingItemComponentRepository; // 需求（追加）：套餐組成
    private final WalletService walletService; // 需求 8-1：消費明細顯示實際套用的折扣種類需要會員折扣率
    private final RetailProductService retailProductService; // 需求（追加）：預約結帳頁加購零售商品

    // 需求（追加，2026-08-27）：營業時間／時段格線改抽到 BusinessHours 共用常數類別，
    // 因為新增的「預設時段容量範本」（DefaultSlotCapacityTemplateService）也需要同一份格線，
    // 避免兩邊各自寫一份、以後改一邊忘了改另一邊。時段長度也從原本固定 2 小時改成 30 分鐘一格。
    private static final LocalTime OPENING = com.petgrooming.pet_system.config.BusinessHours.OPENING;
    private static final LocalTime CLOSING = com.petgrooming.pet_system.config.BusinessHours.CLOSING;

    @Transactional
    public AppointmentResponse book(AppointmentRequest req, String username) {
        return book(req, username, false);
    }

    // 需求（追加，2026-08-30）：不開放顧客自己在 LIFF 當天提出預約申請，
    // 改請顧客直接聯繫官方 LINE 或致電店家處理臨時/當天需求；
    // 店員代客預約（AppointmentMvcController）不受此限制，仍可正常幫顧客
    // 建立當天預約（例如接到顧客來電或 LINE 訊息後手動處理）。
    public AppointmentResponse book(AppointmentRequest req, String username, boolean staffAssisted) {

        // 1a-0. 需求（追加，2026-08-30）：顧客自己（非店員代客）不開放預約「今天」
        if (!staffAssisted && req.getDate().isEqual(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "不開放當天提出預約申請，如需要預約當日，請直接聯繫官方 LINE 或致電 0902-301-820");
        }

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

        // 需求（追加，2026-08-26）：預約日期如果是今天，開始時間不能是已經過去
        // 的時段——前端 LIFF 預約頁已經把過去的時段標成不可選，這裡是後端
        // 防禦性補一道，避免有人繞過前端畫面直接打 API 送出過去時段的預約。
        if (req.getDate().isEqual(LocalDate.now())
                && !req.getStartTime().isAfter(java.time.LocalTime.now())) {
            throw new IllegalArgumentException("這個時段已經過了，請選擇還沒開始的時段");
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

        // 需求（追加）：僅限既有客戶的項目（例如貓咪基礎保養），這隻寵物完全沒有
        // 消費紀錄的話不能線上預約這個項目。
        for (GroomingItem item : actualItems) {
            if (item.isRequiresExistingCustomer()
                    && !petConsumptionHistoryService.hasPriorPaidService(user.getId(), pet.getName(), null)) {
                throw new IllegalArgumentException("「" + item.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法預約此項目");
            }
        }

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

        // 需求（追加，2026-08-26）：新預約送出時（不管當天臨時預約還是待確認），
        // 通知所有已綁定 LINE 的店家/員工帳號，讓店家第一時間知道有新單進來，
        // 跟低庫存通知同一套「發給所有已綁定 LINE 的員工/店家帳號」機制。
        notifyStaffNewBooking(saved);

        // 1i. 發送通知（原本的舊版模擬通知，實際上只印 console log，非真正 LINE 推播，先保留不動）
        notificationService.sendBookingConfirmation(
                user.getUsername(), pet.getName(), req.getDate(), req.getStartTime());
        notificationService.scheduleReminder(
                user.getUsername(), req.getDate(), req.getStartTime());

        // 需求 13：當天臨時預約已直接視為已確認，立即發送真正的 LINE 確認通知
        // （非當天的預約要等店家在後台點「確認預約」，由 confirm() 方法發送）
        if (isSameDayBooking) {
            sendConfirmedNotify(saved);
            // 需求（追加）：Google 日曆串接——只同步「已確認」狀態的預約，
            // 待確認的預約時間可能還會變動，等真的確認了才佔用店家日曆版面。
            googleCalendarService.syncEvent(saved);
            appointmentRepository.save(saved); // 上面那行如果同步成功會寫入 googleCalendarEventId，補存一次
        }

        return AppointmentResponse.from(saved);
    }

    // 需求（追加，2026-08-26）：新預約送出時通知店家/員工，跟 StoreSupplyService
    // 低庫存通知同一套「發給所有已綁定 LINE 的員工/店家帳號」機制，不用另外設計。
    // 推播失敗（token 沒設定、帳號沒綁 LINE 等）不應該讓預約本身失敗，
    // pushText() 內部已經處理過這個容錯，這裡不用再包 try-catch。
    private void notifyStaffNewBooking(Appointment appointment) {
        String statusText = appointment.getStatus() == AppointmentStatus.CONFIRMED
                ? "（當天預約，已自動確認）" : "（待確認）";
        String text = String.format(
                "📅【新預約通知】%s%s\n毛孩：%s（%s）\n時間：%s %s~%s\n顧客：%s\n金額：$%d",
                appointment.getPetName(), statusText,
                appointment.getPetName(), appointment.getPetType(),
                appointment.getDate(), appointment.getStartTime(), appointment.getEndTime(),
                appointment.getUser().getName(),
                appointment.getTotalAmount());

        List<User> staffAndAdmin = new java.util.ArrayList<>(userRepository.findByRole(UserRole.STAFF));
        staffAndAdmin.addAll(userRepository.findByRole(UserRole.ADMIN));
        for (User u : staffAndAdmin) {
            if (u.getLineUserId() != null && !u.getLineUserId().isBlank()) {
                lineMessagingService.pushText(u.getLineUserId(), text);
            }
        }
    }


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

        // 需求（追加）：Google 日曆串接——取消的同時把店家日曆上對應的事件刪掉
        googleCalendarService.deleteEvent(saved);

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
        List<Appointment> appointments = appointmentRepository.findAll();
        // 需求（追加，2026-08-26 修正）：批次撈出所有這些預約核對後的實際項目，
        // 一次查完分組好，不要在迴圈裡一筆一筆查（避免 N+1）。
        java.util.Map<Long, List<com.petgrooming.pet_system.model.AppointmentItem>> checkinItemsByAppt =
                appointmentItemRepository.findByAppointmentIdIn(
                                appointments.stream().map(Appointment::getId).toList())
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                ci -> ci.getAppointment().getId()));

        return appointments.stream()
                .map(a -> {
                    var res = AppointmentResponse.from(a);
                    res.setDisplayItemNames(buildDisplayItemNames(a, checkinItemsByAppt.get(a.getId())));
                    return res;
                })
                .toList();
    }

    // 需求（追加，2026-08-26 修正）：核對過（有 AppointmentItem 紀錄）就用核對
    // 後的實際項目名稱（只取有計價的主項目，$0 的副組成拆分明細不放進列表摘要，
    // 那些本來就是給待補經手人矩陣拆積分用的，不是給顧客/店員看「選了什麼」的）；
    // 還沒核對過（checkinItems 是 null 或空）就照舊用 selectedItems 的名稱。
    private List<String> buildDisplayItemNames(Appointment a,
            List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems) {
        if (checkinItems != null && !checkinItems.isEmpty()) {
            return checkinItems.stream()
                    .filter(ci -> ci.getPrice() > 0)
                    .map(com.petgrooming.pet_system.model.AppointmentItem::getItemName)
                    .toList();
        }
        return a.getSelectedItems().stream()
                .map(com.petgrooming.pet_system.model.GroomingItem::getName)
                .toList();
    }

    // ── 店家後台查詢所有預約（含內部備注，需求 7）──────────────────────────
    public List<com.petgrooming.pet_system.dto.AppointmentAdminResponse> getAllForAdmin() {
        List<Appointment> appointments = appointmentRepository.findAll();
        // 需求（追加，2026-08-26 修正）：同 getAllAppointments() 的說明，批次撈核對後項目。
        java.util.Map<Long, List<com.petgrooming.pet_system.model.AppointmentItem>> checkinItemsByAppt =
                appointmentItemRepository.findByAppointmentIdIn(
                                appointments.stream().map(Appointment::getId).toList())
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                ci -> ci.getAppointment().getId()));

        return appointments.stream()
                .map(a -> {
                    var res = com.petgrooming.pet_system.dto.AppointmentAdminResponse.from(a);
                    res.setDisplayItemNames(buildDisplayItemNames(a, checkinItemsByAppt.get(a.getId())));
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
            LocalTime next = current.plusMinutes(com.petgrooming.pet_system.config.BusinessHours.SLOT_MINUTES);
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

        // 需求（追加）：Google 日曆串接——確認的同時同步進店家共用日曆
        googleCalendarService.syncEvent(saved);
        appointmentRepository.save(saved); // 補存 googleCalendarEventId

        return AppointmentResponse.from(saved);
    }

    // 需求 13：預約確認通知文字內容 + 發送，供「店家點確認」與「當日臨時預約自動確認」共用
    // 需求（追加，2026-08-30）：文案改版（店家指定新內容），改成完整的預約須知格式。
    private void sendConfirmedNotify(Appointment saved) {
        String dateStr = saved.getDate().format(java.time.format.DateTimeFormatter.ofPattern("M/d"));
        String notifyText = String.format(
                "【慕沐村 Mulage pet】%n" +
                        "〔✓ 預約確認〕%n" +
                        "%s  %s%n" +
                        "%s的美容預約已確認 ♡%n%n" +
                        "⏰ 預約提醒%n" +
                        "預約前一天將再次傳送訊息提醒您%n%n" +
                        "𓂃 預約時間%n" +
                        "・狗狗最早可於預約時間前 30 分鐘抵達%n" +
                        "・遲到超過 20 分鐘，將自動取消當日預約，並視同臨時取消%n" +
                        "・本店採全預約制，如需改期請提前告知%n%n" +
                        "⌂ 接回時間%n" +
                        "美容完成後請於 2 小時內接回%n" +
                        "逾時將酌收一次性延遲服務費$200%n" +
                        "如有特殊情況，請提前與我們詢問ɞ%n%n" +
                        "୨୧ 取消／異動%n" +
                        "預約前 24 小時內臨時取消，下次預約需支付50%% 訂金；未赴約訂金恕不退還。%n" +
                        "會員臨時取消，當次取消費用（美容費用30%%）將直接由儲值金扣除。%n%n" +
                        "🐾 美容小提醒%n" +
                        "・腳底毛皆採平剃，如有特殊需求請提前告知%n" +
                        "・為確保毛孩安全，現場採門禁管理，恕不開放家長等候%n%n" +
                        "✦ 營業時間%n" +
                        "最後接狗／貓時間為 19:30%n%n" +
                        "感謝家長的配合與理解%n" +
                        "期待與您和寶貝們相見♡",
                dateStr, saved.getStartTime(), saved.getPetName());
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

    // 需求（追加，2026-08-30）：前一天預約提醒文案改版（店家指定新內容），
    // 補回寵物名字（原始文案沒寫，店家確認要保留）。
    // 原本這裡有一段「隔天預約時間早於 11:00 要額外提醒準時到場」的邏輯
    // （EARLY_SLOT_CUTOFF），但店家開門時間本來就是 11:00，條件永遠不會成立，
    // 是早就存在的死代碼，新範本也沒有適合放這句提醒的位置，順手一併拿掉。
    private String buildReminderText(Appointment appointment) {
        return String.format(
                "【慕沐村 Mulage pet】%n" +
                        "🔔預約提醒 🔔%n" +
                        "***明天 %s %s%n" +
                        "%s有預約洗香香 𓈒𓏸%n" +
                        "🚗開車前來的家長%n" +
                        "可於社區大門外短暫停靠接送%n" +
                        "請勿停放於「車道出入口」%n" +
                        "明天見 ʚ",
                appointment.getDate(), appointment.getStartTime(), appointment.getPetName());
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
        // 需求（追加，2026-08-30）：文案改版（店家指定新內容）。
        String notifyText = String.format(
                "【慕沐村 Mulage pet】%n" +
                        "𓂃 ✦ 美容完成 ✦ 𓂃%n" +
                        "%s的美容服務完成囉 ɞ%n" +
                        "再麻煩家長於 2 小時內%n" +
                        "前來接寶貝回家 ♡",
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

            // 需求（追加）：僅限既有客戶的項目，這隻寵物完全沒有消費紀錄的話不能選這個項目。
            if (gi.isRequiresExistingCustomer()
                    && !petConsumptionHistoryService.hasPriorPaidService(
                            appointment.getUser().getId(), appointment.getPetName(), appointmentId)) {
                throw new IllegalArgumentException("「" + gi.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法選擇此項目");
            }

            // 需求（追加）：主項目本身掛的積分分類（例如 BATH_CAT_S）如果有副組成，
            // 名稱後面比照副組成加上分類標籤（例如「（洗（小貓））」），店家在待補
            // 經手人矩陣表單才看得出「這行就是洗澡的積分」，不會誤以為 BATH 沒出現
            // （這行其實一直都有記錄，只是原本沒有標籤，容易被誤認成普通項目名稱）。
            var itemComponents = groomingItemComponentRepository.findByGroomingItemId(gi.getId());
            String mainItemName = itemComponents.isEmpty()
                    ? gi.getName()
                    : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）";

            com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .groomingItemId(gi.getId())
                    .itemName(mainItemName)
                    .price((int) Math.round(gi.getPrice()))
                    .points(gi.getPoints())
                    .performanceCategory(gi.getPerformanceCategory())
                    .build();
            appointmentItemRepository.save(item);
            expandPackageComponents(appointment, gi, itemComponents); // 需求（追加）：套餐化——展開副組成
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

    // ── 需求（追加）：這隻寵物是不是既有客戶（供畫面過濾「僅限既有客戶」項目用）───
    public boolean isExistingCustomerPet(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        return petConsumptionHistoryService.hasPriorPaidService(
                appointment.getUser().getId(), appointment.getPetName(), appointmentId);
    }

    // 需求（追加）：這筆預約的寵物種類（供畫面過濾「適用物種」項目用）
    public String getPetTypeForAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"))
                .getPetType();
    }

    // 需求（追加，2026-08-24）：狗狗定價流程簡化——這筆預約對應的完整寵物資料
    // （含目前體重、鎖定的固定套餐），供結帳完成後的體重提醒彈窗使用。
    // 查不到對應寵物（理論上不該發生，預約一定綁著某隻已建檔的寵物）就回傳 null。
    public com.petgrooming.pet_system.dto.PetResponse getPetForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        var pet = petRepository.findByOwnerUsernameAndName(
                appointment.getUser().getUsername(), appointment.getPetName()).orElse(null);
        if (pet == null) return null;
        var lockedItem = pet.getLockedGroomingItemId() != null
                ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                : null;
        return com.petgrooming.pet_system.dto.PetResponse.from(pet, lockedItem);
    }

    // ── 需求（追加）：編輯訂單——結帳前新增一筆美容服務項目 ────────────────
    // 跟加購零售商品同一套「結帳前才准動」的限制；核對時發現漏開/開錯項目，
    // 不用整筆退款重開，直接在這裡補上即可。
    @Transactional
    // 需求（追加，2026-08-26）：customPrice 選填，跟 WalkInOrderService 同一套
    // 設計，積分固定照 gi.getPoints() 計算，跟改過的價格完全無關。
    public void addGroomingItem(Long appointmentId, Long groomingItemId, String username) {
        addGroomingItem(appointmentId, groomingItemId, null, username);
    }

    public void addGroomingItem(Long appointmentId, Long groomingItemId, Integer customPrice, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }

        GroomingItem gi = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到服務項目"));

        // 需求（追加）：僅限既有客戶的項目（例如貓咪基礎保養），這隻寵物完全沒有
        // 消費紀錄的話不能加入這個項目。
        if (gi.isRequiresExistingCustomer()
                && !petConsumptionHistoryService.hasPriorPaidService(
                        appointment.getUser().getId(), appointment.getPetName(), appointmentId)) {
            throw new IllegalArgumentException("「" + gi.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法加入此項目");
        }

        // 需求（追加）：主項目本身掛的積分分類如果有副組成，名稱後面比照副組成
        // 加上分類標籤（見上方 checkin 流程同樣的說明）。有自訂價格的話，名稱
        // 後面也標一下「（自訂價格）」，方便日後對帳時一眼看出這筆不是原價。
        var itemComponents = groomingItemComponentRepository.findByGroomingItemId(gi.getId());
        boolean hasCustomPrice = customPrice != null && customPrice >= 0;
        String mainItemName = (itemComponents.isEmpty() ? gi.getName()
                : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）")
                + (hasCustomPrice ? "（自訂價格）" : "");
        int actualPrice = hasCustomPrice ? customPrice : (int) Math.round(gi.getPrice());

        com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                .builder()
                .appointment(appointment)
                .groomingItemId(gi.getId())
                .itemName(mainItemName)
                .price(actualPrice)
                .points(gi.getPoints()) // 積分固定照項目原本設定，不受自訂價格影響
                .performanceCategory(gi.getPerformanceCategory())
                .build();
        appointmentItemRepository.save(item);
        expandPackageComponents(appointment, gi, itemComponents); // 需求（追加）：套餐化——展開副組成

        appointment.setTotalAmount(appointment.getTotalAmount() + item.getPrice());
        appointment.setCheckinOrderConfirmed(true); // 保險起見一併標記，避免極少數尚未開過單的情況卡在後續流程
        appointmentRepository.save(appointment);

        log.info("預約 #{} 編輯新增服務項目「{}」", appointmentId, gi.getName());
    }

    // ── 需求（追加，2026-08-26）：自訂金額加購 ──────────────────────────────
    // 用途：處理「高階定制調理」（開放式報價）跟各種浮動加價（厚毛/長毛/
    // 特殊剪法/特殊情況）——這些沒辦法用固定價目表項目涵蓋，店員需要現場
    // 依實際情況打一個自訂名稱+金額的項目進去，不綁定任何現有的 GroomingItem。
    // 積分分類由店員自己選（下拉選單挑一個既有的績效分類），積分固定套用
    // 那個分類的預設積分（跟 GroomingItem 新增時「沒填積分就用分類預設值」
    // 同一套邏輯）；不參與任何折扣（discountEligible 固定 false），因為這種
    // 客製化報價本來就是店員當下依實際情況談定的金額，不應該再疊加折扣。
    public void addCustomItem(Long appointmentId, String itemName, int price,
            com.petgrooming.pet_system.enums.PerformanceCategory category, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該預約"));
        if (appointment.isPaid()) {
            throw new IllegalArgumentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("請填寫項目名稱");
        }
        if (price < 0) {
            throw new IllegalArgumentException("金額不能是負數");
        }

        var actualCategory = category != null ? category : com.petgrooming.pet_system.enums.PerformanceCategory.OTHER;
        com.petgrooming.pet_system.model.AppointmentItem item = com.petgrooming.pet_system.model.AppointmentItem
                .builder()
                .appointment(appointment)
                .groomingItemId(null) // 不綁定任何現有服務項目
                .itemName(itemName.trim() + "（自訂項目）")
                .price(price)
                .points(actualCategory.getDefaultPoints())
                .performanceCategory(actualCategory)
                .build();
        appointmentItemRepository.save(item);

        appointment.setTotalAmount(appointment.getTotalAmount() + price);
        appointment.setCheckinOrderConfirmed(true);
        appointmentRepository.save(appointment);

        log.info("預約 #{} 新增自訂項目「{}」，金額 ${}", appointmentId, itemName, price);
    }

    // 需求（追加）：套餐化——一個套餐項目結帳/開單時，除了自己的主組成（已經記錄在
    // AppointmentItem 本身），如果後台有設定「副組成」，這裡一併展開成額外的待補經手人
    // 紀錄（price 固定 0，不重複計價，純粹是為了矩陣待補經手人表單能拆出每個積分分類，
    // 可能由不同美容師分開處理）。
    // components 參數由呼叫端先查好傳進來，避免跟「要不要加主項目標籤」那次查詢重複查兩次。
    private void expandPackageComponents(Appointment appointment, GroomingItem gi,
            List<com.petgrooming.pet_system.model.GroomingItemComponent> components) {
        for (var component : components) {
            com.petgrooming.pet_system.model.AppointmentItem sub = com.petgrooming.pet_system.model.AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName() + "（" + component.getPerformanceCategory().getLabel() + "）")
                    .price(0)
                    .points(component.getPoints())
                    .performanceCategory(component.getPerformanceCategory())
                    .build();
            appointmentItemRepository.save(sub);
        }
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
        // 需求（追加，2026-08-26 修正）：軟刪除的寵物不出現在預約下拉選單裡
        return petRepository.findByOwnerUsername(username).stream()
                .filter(pet -> !pet.isDeleted())
                .toList();
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
        boolean firstVisitEligible = dogFirstVisitDiscountService.isFirstVisitEligible(appointment); // 需求（追加）
        boolean catFirstVisitEligible = catFirstVisitDiscountService.isFirstVisitEligible(appointment); // 需求（追加）

        List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems = appointmentItemRepository
                .findByAppointmentId(appointmentId);

        List<com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem> items;
        if (!checkinItems.isEmpty()) {
            items = checkinItems.stream()
                    .map(ci -> {
                        // 需求（追加，2026-08-24）：這次順便撈出 dogWeightTier，跟原本查
                        // memberEligible 用同一次 findById，不用再多查一次資料庫。
                        var groomingItemOpt = ci.getGroomingItemId() != null
                                ? groomingItemRepository.findById(ci.getGroomingItemId())
                                : java.util.Optional.<com.petgrooming.pet_system.model.GroomingItem>empty();
                        boolean memberEligible = groomingItemOpt
                                .map(com.petgrooming.pet_system.model.GroomingItem::isDiscountEligible)
                                .orElse(true);
                        boolean rewashApplicable = rewashEligible
                                && catRewashDiscountService.isCatBathCategory(ci.getPerformanceCategory());
                        boolean firstVisitApplicable = firstVisitEligible
                                && dogFirstVisitDiscountService.isDogPackageCategory(ci.getPerformanceCategory());
                        boolean catFirstVisitApplicable = catFirstVisitEligible
                                && catFirstVisitDiscountService.isCatBathCategory(ci.getPerformanceCategory());
                        return com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                                .itemId(ci.getId())
                                .groomingItemId(ci.getGroomingItemId())
                                .dogWeightTier(groomingItemOpt
                                        .map(gi -> gi.getDogWeightTier() != null ? gi.getDogWeightTier().name() : null)
                                        .orElse(null))
                                .name(ci.getItemName())
                                .price(ci.getPrice())
                                .operatorName(ci.getOperatorStaff() != null ? ci.getOperatorStaff().getName() : null)
                                .discountEligible(memberEligible)
                                .rewashEligible(rewashApplicable)
                                .firstVisitEligible(firstVisitApplicable || catFirstVisitApplicable)
                                .retailItem(ci.getRetailProductId() != null)
                                .appliedDiscountType(transactionOpt.isPresent()
                                        ? resolveAppliedDiscountType(rewashApplicable, firstVisitApplicable, catFirstVisitApplicable, memberEligible && paidByWallet, memberDiscountRate)
                                        : null)
                                .build();
                    })
                    .toList();
        } else {
            items = appointment.getSelectedItems().stream()
                    .map(gi -> {
                        boolean rewashApplicable = rewashEligible && catRewashDiscountService.isCatBathItem(gi);
                        boolean firstVisitApplicable = firstVisitEligible && dogFirstVisitDiscountService.isDogPackageItem(gi);
                        boolean catFirstVisitApplicable = catFirstVisitEligible && catFirstVisitDiscountService.isCatBathItem(gi);
                        return com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                                .groomingItemId(gi.getId())
                                .dogWeightTier(gi.getDogWeightTier() != null ? gi.getDogWeightTier().name() : null)
                                .name(gi.getName())
                                .price((int) Math.round(gi.getPrice()))
                                .operatorName(null)
                                .discountEligible(gi.isDiscountEligible())
                                .rewashEligible(rewashApplicable)
                                .firstVisitEligible(firstVisitApplicable || catFirstVisitApplicable)
                                .appliedDiscountType(transactionOpt.isPresent()
                                        ? resolveAppliedDiscountType(rewashApplicable, firstVisitApplicable, catFirstVisitApplicable, gi.isDiscountEligible() && paidByWallet, memberDiscountRate)
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
    // 需求（追加）：firstVisitApplicable 為 true 時優先用狗狗首次體驗優惠的判斷結果
    // （跟 calculateAmountWithRewashDiscount 一致：一個項目不可能同時符合兩種資格，
    // 因為分屬貓/狗互斥的品種分類，這裡優先分支不影響正確性）。
    private com.petgrooming.pet_system.enums.DiscountType resolveAppliedDiscountType(
            boolean rewashApplicable, boolean firstVisitApplicable, boolean catFirstVisitApplicable,
            boolean memberApplicable, double memberDiscountRate) {
        if (firstVisitApplicable) {
            return dogFirstVisitDiscountService
                    .resolvePreferredDiscount(100.0, true, memberApplicable, memberDiscountRate)
                    .type();
        }
        if (catFirstVisitApplicable) {
            return catFirstVisitDiscountService
                    .resolvePreferredDiscount(100.0, true, memberApplicable, memberDiscountRate)
                    .type();
        }
        return catRewashDiscountService
                .resolvePreferredDiscount(100.0, rewashApplicable, memberApplicable, memberDiscountRate)
                .type();
    }
}