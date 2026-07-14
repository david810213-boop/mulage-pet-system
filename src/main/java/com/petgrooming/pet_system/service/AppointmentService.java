package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.NotificationService;
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

    private static final LocalTime OPENING   = LocalTime.of(11, 0);
    private static final LocalTime CLOSING   = LocalTime.of(19, 0);
    private static final int       SLOT_HOURS = 2;

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

        // 1f. 確認時段沒有重疊（已取消的預約不算佔用）
        boolean overlap = appointmentRepository
                .findByDate(req.getDate()).stream()
                .filter(a -> !a.isCancelled())
                .anyMatch(a -> a.getStartTime().isBefore(req.getEndTime())
                        && a.getEndTime().isAfter(req.getStartTime()));
        if (overlap) {
            throw new IllegalArgumentException("該時段已被預約，請選擇其他時段");
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

        appointment.setStatus(com.petgrooming.pet_system.enums.AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason(req != null ? req.getReason() : null);
        appointment.setCancelledBy(isOwner
                ? "會員自行取消（" + user.getName() + "）"
                : "員工取消：" + user.getName());

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

    // ── 查詢可預約時段 ────────────────────────────────────────────────────
    public List<TimeSlotResponse> getAvailableSlots(LocalDate date) {
        List<TimeSlotResponse> allSlots = new ArrayList<>();
        LocalTime current = OPENING;
        while (current.isBefore(CLOSING)) {
            LocalTime next = current.plusHours(SLOT_HOURS);
            if (next.isAfter(CLOSING)) next = CLOSING;
            allSlots.add(new TimeSlotResponse(current, next, true));
            current = next;
        }

        List<Appointment> booked = appointmentRepository.findByDate(date)
                .stream().filter(a -> !a.isCancelled()).toList();
        for (TimeSlotResponse slot : allSlots) {
            for (Appointment a : booked) {
                boolean overlap =
                        a.getStartTime().isBefore(slot.getEndTime()) &&
                        a.getEndTime().isAfter(slot.getStartTime());
                if (overlap) {
                    slot.setAvailable(false);
                    break;
                }
            }
        }
        return allSlots;
    }

    // ── 2. 取得使用者的寵物清單（預約表單下拉選單用）──────────────────
    public List<Pet> getMyPetsForBooking(String username) {
        return petRepository.findByOwnerUsername(username);
    }
}