package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.NotificationService;
import com.petgrooming.pet_system.notification.LineMessagingService;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
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
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final GroomingItemRepository groomingItemRepository; // ⚡ 1. 補上這行注入，用來撈取服務價格
    private final NotificationService notificationService;
    private final LineMessagingService lineMessagingService;
    private final SlotCapacityService slotCapacityService;       // 需求 3：時段名額控管

    private static final LocalTime OPENING   = LocalTime.of(11, 0);
    private static final LocalTime CLOSING   = LocalTime.of(19, 0);
    private static final int       SLOT_HOURS = 2;
    private static final int       SLOT_CAPACITY = 5;            // 同時段最多 5 隻

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

        // 1f. 需求 3：同時段最多 5 隻（併發安全）。
        //     先確保計數列存在（獨立交易），再於本交易內加悲觀鎖 +1；額滿則丟出例外。
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

        boolean isOwner        = appointment.getUser().getId().equals(user.getId());
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

        if (internalNote != null) appointment.setInternalNote(internalNote);
        if (memberNote != null)   appointment.setMemberNote(memberNote);

        Appointment saved = appointmentRepository.save(appointment);
        return com.petgrooming.pet_system.dto.AppointmentAdminResponse.from(saved);
    }

    // ── 查詢可預約時段（需求 3：回傳每個時段剩餘名額，上限 5）──────────────
    public List<TimeSlotResponse> getAvailableSlots(LocalDate date) {
        List<TimeSlotResponse> allSlots = new ArrayList<>();
        LocalTime current = OPENING;
        while (current.isBefore(CLOSING)) {
            LocalTime next = current.plusHours(SLOT_HOURS);
            if (next.isAfter(CLOSING)) next = CLOSING;

            int booked    = slotCapacityService.bookedCount(date, current);
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
                saved.getPetName()
        );
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);

        return AppointmentResponse.from(saved);
    }

    // ── 2. 取得使用者的寵物清單（預約表單下拉選單用）──────────────────
    public List<Pet> getMyPetsForBooking(String username) {
        return petRepository.findByOwnerUsername(username);
    }
}