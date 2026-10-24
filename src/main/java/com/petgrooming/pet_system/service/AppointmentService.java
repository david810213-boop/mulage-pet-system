package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.config.StoreRules;
import com.petgrooming.pet_system.config.BusinessHours;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.utils.ContractInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 預約：建立（時段名額、契約簽名、首次評估）、取消、確認、店家備註。
 *
 * 其他職責分在：{@link AppointmentQueryService}（查詢、可預約時段）、
 * {@link AppointmentCheckInService}（到店開單、開始與結束服務、核對）、
 * {@link AppointmentItemService}（結帳前編輯項目、經手人與積分）、
 * {@link AppointmentDetailService}（預約明細）、{@link AppointmentNotificationService}（LINE 通知）、
 * {@link AppointmentListService}（列表篩選）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentNotificationService appointmentNotificationService; // LINE 通知
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final GroomingItemRepository groomingItemRepository; // 服務項目價格
    private final GoogleCalendarService googleCalendarService; // Google 日曆串接
    private final SlotCapacityService slotCapacityService; // 時段名額控管
    private final ClosedDateService closedDateService; // 公休日設定
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 僅限既有客戶項目判斷

    // 營業時間／時段格線（共用 BusinessHours）
    private static final LocalTime OPENING = BusinessHours.OPENING;
    private static final LocalTime CLOSING = BusinessHours.CLOSING;

    @Transactional
    public AppointmentResponse book(AppointmentRequest req, String username) {
        return book(req, username, false);
    }

    /**
     * 建立預約。顧客自己在 LIFF 預約時 staffAssisted 為 false；店員代客預約為 true（可預約當天、初次預約可選項目）。
     *
     * 整個流程在同一個交易內：時段名額在檢查項目之前就先扣，之後任何一步失敗都會整筆退回，名額不會被佔走。
     * 檢查順序即錯誤訊息的優先順序，調整前請確認不會改變使用者看到的訊息。
     */
    @Transactional
    public AppointmentResponse book(AppointmentRequest req, String username, boolean staffAssisted) {
        validateBookingDate(req.getDate(), staffAssisted);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        Pet pet = findOwnedPet(req.getPetId(), user);
        validateTimeSlot(req);
        String signature = requireContractSignature(req.getContractSignatureData());

        reserveSlotFor(pet, req.getDate(), req.getStartTime());

        boolean returningPet = petConsumptionHistoryService.hasPriorPaidService(user.getId(), pet.getName(), null);
        boolean assessment = isFirstVisitAssessment(req, returningPet, staffAssisted);
        if (assessment) {
            rejectIfAssessmentPending(user, pet);
        }
        List<GroomingItem> items = assessment ? new ArrayList<>() : resolveBookableItems(req.getSelectedItems(), returningPet);

        Appointment saved = appointmentRepository.save(
                newAppointment(req, user, pet, items, signature, assessment));
        log.info("[預約建立] #{} 寫入完成：金額 {}，項目 {} 項", saved.getId(), saved.getTotalAmount(), items.size());

        appointmentNotificationService.notifyBooked(saved, user, pet, req);
        return AppointmentResponse.from(saved);
    }

    /** 任何人都不能預約過去的日期；顧客自己不能預約當天（請改聯繫店家），店員代客可以。 */
    private static void validateBookingDate(LocalDate date, boolean staffAssisted) {
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new AppointmentException("不能預約已經過去的日期，請重新選擇");
        }
        if (!staffAssisted && date.isEqual(LocalDate.now())) {
            throw new AppointmentException(
                    "不開放當天提出預約申請，如需要預約當日，請直接聯繫官方 LINE 或致電 0902-301-820");
        }
    }

    /** 寵物必須存在且屬於預約人；不屬於時回同樣的訊息，不透露寵物是否存在。 */
    private Pet findOwnedPet(Long petId, User user) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new AppointmentException("找不到寵物，請先至「我的寵物」新增後再預約"));
        if (!pet.getOwner().getId().equals(user.getId())) {
            throw new AppointmentException("找不到寵物，請先至「我的寵物」新增後再預約");
        }
        return pet;
    }

    /** 公休日、營業時間、起訖順序，以及當天預約不能選已經過去的時段。 */
    private void validateTimeSlot(AppointmentRequest req) {
        if (closedDateService.isClosed(req.getDate())) {
            throw new AppointmentException("該日為公休日，恕不開放預約，請選擇其他日期");
        }
        if (req.getStartTime().isBefore(OPENING) || req.getEndTime().isAfter(CLOSING)) {
            throw new AppointmentException(
                    "超出營業時間！請輸入 " + OPENING + " – " + CLOSING + " 之間的時間");
        }
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new AppointmentException("結束時間必須晚於開始時間");
        }
        if (req.getDate().isEqual(LocalDate.now()) && !req.getStartTime().isAfter(LocalTime.now())) {
            throw new AppointmentException("這個時段已經過了，請選擇還沒開始的時段");
        }
    }

    /**
     * 定型化契約必須有實際簽名。空白畫布匯出的 data URL 通常只有一兩百字元，
     * 有筆劃的簽名會明顯更長，以長度門檻判斷是否真的簽了名。
     */
    private static String requireContractSignature(String signatureData) {
        String signature = signatureData == null ? "" : signatureData.trim();
        if (!signature.startsWith("data:image") || signature.length() < StoreRules.MIN_SIGNATURE_LENGTH) {
            throw new AppointmentException("請詳閱定型化契約，並在簽名板上親筆簽名後再送出預約");
        }
        return signature;
    }

    /**
     * 佔用一個時段名額。時段可限定只開放特定物種（避免同時段狗貓混雜），
     * 這個限制關乎實際在店裡的寵物，因此店員代客預約也要遵守。
     */
    private void reserveSlotFor(Pet pet, LocalDate date, LocalTime startTime) {
        slotCapacityService.ensureSlot(date, startTime);
        PetType allowedPetType = slotCapacityService.getAllowedPetType(date, startTime);
        if (allowedPetType != null && allowedPetType != pet.getPetType()) {
            throw new AppointmentException(
                    "此時段目前僅開放" + allowedPetType.getDescription() + "預約，請選擇其他時段");
        }
        slotCapacityService.reserve(date, startTime);
    }

    /**
     * 初次預約（這隻毛孩沒有已付款紀錄）只選時段，由店家到店評估報價：
     * 顧客自己預約一律如此；店員代客預約若沒選項目也是，有選項目則照一般流程。
     */
    private static boolean isFirstVisitAssessment(AppointmentRequest req, boolean returningPet, boolean staffAssisted) {
        boolean noItems = req.getSelectedItems() == null || req.getSelectedItems().isEmpty();
        return !returningPet && (!staffAssisted || noItems);
    }

    /** 同一隻毛孩的初次預約還沒完成前，不能再送出另一筆初次預約。 */
    private void rejectIfAssessmentPending(User user, Pet pet) {
        boolean pending = appointmentRepository
                .findByUserIdAndPetNameAndFirstVisitAssessmentTrueAndPaidFalse(user.getId(), pet.getName())
                .stream().anyMatch(a -> !a.isCancelled());
        if (pending) {
            throw new AppointmentException("「" + pet.getName() + "」已經有一筆初次預約還沒完成，"
                    + "完成第一次美容後就能再預約；如需改時間請聯繫官方 LINE");
        }
    }

    /**
     * 把前端送來的項目代碼換成資料庫中仍在販售的項目，並檢查「僅限既有客戶」的項目。
     * 回傳可變清單：交給 Hibernate 寫入多對多關聯時不能是唯讀清單（曾因此導致預約寫入後沒有項目）。
     */
    private List<GroomingItem> resolveBookableItems(List<String> itemCodes, boolean returningPet) {
        if (itemCodes == null || itemCodes.isEmpty()) {
            throw new AppointmentException("請至少選擇一項美容服務項目！");
        }
        List<GroomingItem> items = new ArrayList<>();
        for (String itemCode : itemCodes) {
            items.add(groomingItemRepository.findByItemCodeAndIsDeletedFalse(itemCode)
                    .orElseThrow(() -> new AppointmentException("服務項目已下架或不存在：" + itemCode)));
        }
        log.info("[預約建立] 收到項目代碼 {} → 實際項目 {}", itemCodes,
                items.stream().map(i -> i.getItemCode() + "=$" + i.getPrice()).toList());
        for (GroomingItem item : items) {
            if (item.isRequiresExistingCustomer() && !returningPet) {
                throw new AppointmentException("「" + item.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法預約此項目");
            }
        }
        return items;
    }

    /**
     * 當天預約直接視為已確認（待確認的緩衝是留給提早預約安排行程用的）；其他預約等店家確認時間。
     * 品項是快照：之後服務項目改名或改價，不影響這筆預約。
     */
    private Appointment newAppointment(AppointmentRequest req, User user, Pet pet, List<GroomingItem> items,
                                       String signature, boolean assessment) {
        boolean sameDay = req.getDate().isEqual(LocalDate.now());
        int total = (int) items.stream().mapToDouble(GroomingItem::getPrice).sum();
        Appointment appointment = Appointment.builder()
                .user(user)
                .petName(pet.getName())
                .petType(pet.getPetType().name())
                .date(req.getDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .selectedItems(items)
                .totalAmount(total)
                .paid(false)
                .status(sameDay ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING_CONFIRM)
                .confirmedTime(sameDay ? LocalDateTime.now() : null)
                .contractSignatureImage(signature)
                .contractAgreedAt(LocalDateTime.now())
                .contractVersion(ContractInfo.VERSION)
                .photoShareConsent(Boolean.TRUE.equals(req.getPhotoShareConsent()))
                .firstVisitAssessment(assessment)
                .build();
        if (req.getStaffId() != null) {
            userRepository.findById(req.getStaffId()).ifPresent(appointment::setStaff);
        }
        return appointment;
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId, CancelAppointmentRequest req, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));

        boolean isOwner = appointment.getUser().getId().equals(user.getId());
        boolean isStaffOrAdmin = user.isStaffOrAdmin();

        if (!isOwner && !isStaffOrAdmin) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：只能取消自己的預約");
        }

        if (appointment.isCancelled()) {
            throw new AppointmentException("此預約已經是取消狀態");
        }

        // 已結帳的預約需先走退款流程，暫不開放直接取消，避免金流/績效資料不一致
        if (appointment.isPaid()) {
            throw new AppointmentException("此預約已完成結帳，無法直接取消，請先處理退款");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason(req != null ? req.getReason() : null);
        appointment.setCancelledBy(isOwner
                ? "會員自行取消（" + user.getName() + "）"
                : "員工取消：" + user.getName());

        // 釋放該時段名額，讓其他人可以遞補
        slotCapacityService.release(appointment.getDate(), appointment.getStartTime());

        Appointment saved = appointmentRepository.save(appointment);

        // 通知顧客預約已取消（目前為系統 log 模擬，之後可接真實推播）
        log.info("[預約取消] #{}（{} {}）取消人：{}", appointment.getId(),
                appointment.getDate(), appointment.getStartTime(), appointment.getCancelledBy());

        // Google 日曆串接——取消的同時把店家日曆上對應的事件刪掉
        googleCalendarService.deleteEvent(saved);

        return AppointmentResponse.from(saved);
    }

    // ── 店家設定預約備注（雙可見性）────────────────────────────────
    // 只更新有帶值的欄位；internalNote 僅後台可見，memberNote 會員可見。
    @Transactional
    public AppointmentAdminResponse setNotes(
            Long appointmentId, String internalNote, String memberNote, String username) {

        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可編輯備注");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (internalNote != null)
            appointment.setInternalNote(internalNote);
        if (memberNote != null)
            appointment.setMemberNote(memberNote);

        Appointment saved = appointmentRepository.save(appointment);
        return AppointmentAdminResponse.from(saved);
    }

    // ── 店家確認預約並敲定最後時間────────────────────────────────
    // confirmedTime 由店家傳入（可與原申請時間不同）；狀態轉為 CONFIRMED。
    @Transactional
    public AppointmentResponse confirm(Long appointmentId, LocalDateTime confirmedTime, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可確認預約");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (appointment.isCancelled()) {
            throw new AppointmentException("此預約已取消，無法確認");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedTime(confirmedTime != null ? confirmedTime : LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        // 用官方 LINE 通知會員「預約已確認」
        appointmentNotificationService.sendConfirmedNotify(saved);

        // Google 日曆串接——確認的同時同步進店家共用日曆
        googleCalendarService.syncEvent(saved);
        appointmentRepository.save(saved); // 補存 googleCalendarEventId

        return AppointmentResponse.from(saved);
    }
}
