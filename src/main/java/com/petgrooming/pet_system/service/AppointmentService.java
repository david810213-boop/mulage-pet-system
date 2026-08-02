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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
    private final PetGroomingNoteRepository petGroomingNoteRepository; // 進行中核對：毛孩美容狀況歷史
    private final PerformanceService performanceService; // 進行中核對：接待送出積分
    private final com.petgrooming.pet_system.repository.AppointmentItemRepository appointmentItemRepository; // 現場開單（依預約編號）

    private static final LocalTime OPENING = LocalTime.of(11, 0);
    private static final LocalTime CLOSING = LocalTime.of(19, 0);
    private static final int SLOT_HOURS = 2;
    private static final int SLOT_CAPACITY = 5; // 同時段最多 5 隻

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
                // 需求 3：顧客送出後為「待確認」，等店家敲定最後時間
                .status(AppointmentStatus.PENDING_CONFIRM)
                .contractSignatureImage(signatureData)
                .contractAgreedAt(java.time.LocalDateTime.now())
                .build();

        // 若有指派員工，設入（選填）
        if (req.getStaffId() != null) {
            userRepository.findById(req.getStaffId()).ifPresent(appointment::setStaff);
        }

        Appointment saved = appointmentRepository.save(appointment);

        // 1i. 發送通知
        notificationService.sendBookingConfirmation(
                user.getUsername(), pet.getName(), req.getDate(), req.getStartTime());
        notificationService.scheduleReminder(
                user.getUsername(), req.getDate(), req.getStartTime());

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
                .map(com.petgrooming.pet_system.dto.AppointmentAdminResponse::from)
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
        LocalTime current = OPENING;
        while (current.isBefore(CLOSING)) {
            LocalTime next = current.plusHours(SLOT_HOURS);
            if (next.isAfter(CLOSING))
                next = CLOSING;

            int booked = slotCapacityService.bookedCount(date, current);
            int remaining = Math.max(0, SLOT_CAPACITY - booked);
            allSlots.add(new TimeSlotResponse(
                    current, next, remaining > 0, booked, SLOT_CAPACITY, remaining));

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

        // 需求 3：用官方 LINE 通知會員「預約已確認」。
        // 因為每隻寵物的美容所需時間會依體型/毛長/性情而不同，這裡不承諾一個確切完成時間，
        // 而是請會員留意店家後續來電或訊息通知的預計完成時間。
        String notifyText = String.format(
                "【慕沐村 Mulage pet】您好，%s 的美容預約已確認！%n" +
                        "由於每隻毛孩的施作時間會依體型、毛況及個性而有所不同，" +
                        "我們會在施作過程中致電或傳訊息通知您預計完成時間，請留意來電或訊息喔 🐾",
                saved.getPetName());
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);

        return AppointmentResponse.from(saved);
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

    // ── 待補經手人清單（現場開單項目中 operatorStaff 為 null 的）─────────────
    public List<com.petgrooming.pet_system.dto.AppointmentItemResponse> pendingItemOperators() {
        return appointmentItemRepository.findByOperatorStaffIsNull().stream()
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

        List<com.petgrooming.pet_system.model.AppointmentItem> checkinItems = appointmentItemRepository
                .findByAppointmentId(appointmentId);

        List<com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem> items;
        if (!checkinItems.isEmpty()) {
            items = checkinItems.stream()
                    .map(ci -> com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                            .name(ci.getItemName())
                            .price(ci.getPrice())
                            .operatorName(ci.getOperatorStaff() != null ? ci.getOperatorStaff().getName() : null)
                            .build())
                    .toList();
        } else {
            items = appointment.getSelectedItems().stream()
                    .map(gi -> com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem.builder()
                            .name(gi.getName())
                            .price((int) Math.round(gi.getPrice()))
                            .operatorName(null)
                            .build())
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

        transactionRepository.findByAppointmentId(appointmentId).ifPresent(tx -> {
            detailBuilder
                    .paymentMethodLabel(tx.getPaymentMethod() != null ? tx.getPaymentMethod().getDisplayName() : null);
            detailBuilder.paymentTime(tx.getPaymentTime());
            detailBuilder.handledBy(tx.getHandledBy());
            detailBuilder.totalAmount(tx.getFinalAmount());
        });

        return detailBuilder.build();
    }
}