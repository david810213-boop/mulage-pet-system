package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.config.BusinessHours;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.CompanySignature;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.Transaction;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.CompanySignatureRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.utils.ContractInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 預約查詢：我的預約、後台預約清單、可預約時段、初次預約判斷、預約對應的寵物、契約副本。
 */
@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository; // 消費明細用
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final GroomingItemRepository groomingItemRepository; // 服務項目價格
    private final SlotCapacityService slotCapacityService; // 時段名額控管
    private final ClosedDateService closedDateService; // 公休日設定
    private final AppointmentItemRepository appointmentItemRepository; // 現場開單（依預約編號）
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 僅限既有客戶項目判斷
    private final CompanySignatureRepository companySignatureRepository; // 契約副本

    // 營業時間／時段格線（共用 BusinessHours，跟預設時段容量範本同一份）
    private static final LocalTime OPENING = BusinessHours.OPENING;
    private static final LocalTime CLOSING = BusinessHours.CLOSING;

    // ── 查詢自己的預約 ────────────────────────────────────────────────────
    public List<AppointmentResponse> getMyAppointments(String username) {
        return appointmentRepository.findByUserUsername(username)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    // ── 查詢所有預約（STAFF/ADMIN）────────────────────────────────────────
    public List<AppointmentResponse> getAllAppointments() {
        List<Appointment> appointments = appointmentRepository.findAllWithUserAndItems();
        // 批次撈出所有這些預約核對後的實際項目，
        // 一次查完分組好，不要在迴圈裡一筆一筆查（避免 N+1）。
        Map<Long, List<AppointmentItem>> checkinItemsByAppt =
                appointmentItemRepository.findByAppointmentIdIn(
                                appointments.stream().map(Appointment::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                ci -> ci.getAppointment().getId()));

        return appointments.stream()
                .map(a -> {
                    var res = AppointmentResponse.from(a);
                    res.setDisplayItemNames(buildDisplayItemNames(a, checkinItemsByAppt.get(a.getId())));
                    return res;
                })
                .toList();
    }

    // 核對過（有 AppointmentItem 紀錄）就用核對
    // 後的實際項目名稱（只取有計價的主項目，$0 的副組成拆分明細不放進列表摘要，
    // 那些本來就是給待補經手人矩陣拆積分用的，不是給顧客/店員看「選了什麼」的）；
    // 還沒核對過（checkinItems 是 null 或空）就照舊用 selectedItems 的名稱。
    private List<String> buildDisplayItemNames(Appointment a,
            List<AppointmentItem> checkinItems) {
        if (checkinItems != null && !checkinItems.isEmpty()) {
            return checkinItems.stream()
                    .filter(ci -> ci.getPrice() > 0)
                    .map(AppointmentItem::getItemName)
                    .toList();
        }
        if (a.isFirstVisitAssessment() && (a.getSelectedItems() == null || a.getSelectedItems().isEmpty())) {
            return List.of("初次・現場評估"); // 還沒到店開單前的顯示文字
        }
        return a.getSelectedItems().stream()
                .map(GroomingItem::getName)
                .toList();
    }

    // ── LIFF 預約頁選好毛孩後，判斷是不是初次預約 ──
    // 初次＝這隻毛孩沒有已付款紀錄；另外回傳是否已經有一筆未完成的初次預約。
    public Map<String, Object> firstVisitStatus(String username, Long petId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        Pet pet = petRepository.findById(petId)
                .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到寵物"));
        boolean firstVisit = !petConsumptionHistoryService.hasPriorPaidService(user.getId(), pet.getName(), null);
        boolean pending = firstVisit && appointmentRepository
                .findByUserIdAndPetNameAndFirstVisitAssessmentTrueAndPaidFalse(user.getId(), pet.getName())
                .stream().anyMatch(a -> !a.isCancelled());
        return Map.of("firstVisit", firstVisit, "pendingAssessment", pending);
    }

    // ── 店家後台查詢所有預約（含內部備注）──────────────────────────
    // 這支方法是專案裡已知效能問題
    // （Dashboard/預約列表載入慢）的根本原因。原本 findAll() 之後，DTO 轉換
    // 逐筆存取 a.getUser()（觸發一次額外查詢）、a.getSelectedItems()（EAGER
    // 多對多，也是逐筆額外查詢），加上迴圈內對每一筆預約各呼叫一次
    // transactionRepository.findByAppointmentId()查有沒有待對帳的匯款交易，
    // 三個來源疊加，資料一多，單次載入可能觸發幾百條 SQL。現在全部改成批次
    // 查詢：findAllWithUserAndItems() 用 JOIN FETCH 把預約、使用者、選擇項目
    // 一次查完；待對帳交易改成 findByAppointmentIdIn() 一次撈出全部再用 id
    // 分組，迴圈內只查 Map，不再另外打資料庫。
    public List<AppointmentAdminResponse> getAllForAdmin() {
        return toAdminResponses(appointmentRepository.findAllWithUserAndItems());
    }

    /** 單筆預約（後台格式）；找不到時是空值。 */
    public Optional<AppointmentAdminResponse> getForAdmin(Long id) {
        return appointmentRepository.findWithUserAndItemsById(id)
                .map(a -> toAdminResponses(List.of(a)).get(0));
    }

    /** 日期介於 from～to（含頭尾）的預約，新的在前。 */
    public List<AppointmentAdminResponse> getForAdminBetween(LocalDate from, LocalDate to) {
        return toAdminResponses(appointmentRepository.findWithUserAndItemsByDateBetween(from, to));
    }

    /** 日期不早於 from 的待確認預約，新的在前。 */
    public List<AppointmentAdminResponse> getPendingConfirmForAdminFrom(LocalDate from) {
        return toAdminResponses(appointmentRepository.findWithUserAndItemsByStatusFrom(AppointmentStatus.PENDING_CONFIRM, from));
    }

    private List<AppointmentAdminResponse> toAdminResponses(List<Appointment> appointments) {
        Map<Long, List<AppointmentItem>> checkinItemsByAppt =
                appointmentItemRepository.findByAppointmentIdIn(
                                appointments.stream().map(Appointment::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                ci -> ci.getAppointment().getId()));

        // 批次撈出全部預約對應的交易紀錄，取代原本
        // 迴圈內逐筆呼叫 transactionRepository.findByAppointmentId() 的寫法。
        // 一筆預約理論上最多一筆交易（結帳時建立），用 id 分組後每組取第一筆即可。
        Map<Long, Transaction> transactionByAppt =
                transactionRepository.findByAppointmentIdIn(
                                appointments.stream().map(Appointment::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                tx -> tx.getAppointment().getId(),
                                tx -> tx,
                                (first, second) -> first));

        return appointments.stream()
                .map(a -> {
                    var res = AppointmentAdminResponse.from(a);
                    res.setDisplayItemNames(buildDisplayItemNames(a, checkinItemsByAppt.get(a.getId())));
                    // 查這筆預約有沒有「待對帳」的匯款交易（已建立交易但尚未確認收款）
                    var tx = transactionByAppt.get(a.getId());
                    if (tx != null
                            && tx.getPaymentMethod() == PaymentMethod.WIRE_TRANSFER
                            && !tx.isPaid()) {
                        res.setPendingWireTransfer(true);
                        res.setPendingTransactionId(tx.getId());
                    }
                    return res;
                })
                .toList();
    }

    // ── 查詢可預約時段（回傳每個時段剩餘名額，上限 5）──────────────
    public List<TimeSlotResponse> getAvailableSlots(LocalDate date) {
        return getAvailableSlots(date, null);
    }

    // 時段可以限定只開放特定物種預約（避免同時段狗貓混雜，
    // 造成寵物緊迫）。帶入 petType 時，該時段限定的物種跟這隻寵物不符的話，一併反映在
    // available 上（不只是名額滿）；不管有沒有帶 petType，allowedPetType 都會回傳，
    // 讓前端可以標示提示文字（例如「🐕限定」），不用等使用者選到寵物才看得出限制。
    public List<TimeSlotResponse> getAvailableSlots(LocalDate date, PetType petType) {
        List<TimeSlotResponse> allSlots = new ArrayList<>();

        // 公休日當天不開放任何時段，直接回傳空清單
        // （不動 SlotCapacity 底下各時段的 capacity 數字，公休日設定解除後名額設定自動還原）
        if (closedDateService.isClosed(date)) {
            return allSlots;
        }

        LocalTime current = OPENING;
        while (current.isBefore(CLOSING)) {
            LocalTime next = current.plusMinutes(BusinessHours.SLOT_MINUTES);
            if (next.isAfter(CLOSING))
                next = CLOSING;

            int booked = slotCapacityService.bookedCount(date, current);
            int capacity = slotCapacityService.getCapacity(date, current);
            int remaining = Math.max(0, capacity - booked);
            PetType allowedPetType =
                    slotCapacityService.getAllowedPetType(date, current);
            boolean speciesMismatch = petType != null && allowedPetType != null && allowedPetType != petType;
            allSlots.add(new TimeSlotResponse(
                    current, next, remaining > 0 && !speciesMismatch, booked, capacity, remaining,
                    allowedPetType != null ? allowedPetType.name() : null));

            current = next;
        }
        return allSlots;
    }

    // ── 這隻寵物是不是既有客戶（供畫面過濾「僅限既有客戶」項目用）───
    public boolean isExistingCustomerPet(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        return petConsumptionHistoryService.hasPriorPaidService(
                appointment.getUser().getId(), appointment.getPetName(), appointmentId);
    }

    // 這筆預約的寵物種類（供畫面過濾「適用物種」項目用）
    public String getPetTypeForAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"))
                .getPetType();
    }

    // 狗狗定價流程簡化——這筆預約對應的完整寵物資料
    // （含目前體重、鎖定的固定套餐），供結帳完成後的體重提醒彈窗使用。
    // 查不到對應寵物（理論上不該發生，預約一定綁著某隻已建檔的寵物）就回傳 null。
    public PetResponse getPetForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        var pet = petRepository.findByOwnerUsernameAndName(
                appointment.getUser().getUsername(), appointment.getPetName()).orElse(null);
        if (pet == null) return null;
        var lockedItem = pet.getLockedGroomingItemId() != null
                ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                : null;
        return PetResponse.from(pet, lockedItem);
    }

    // ── 2. 取得使用者的寵物清單（預約表單下拉選單用）──────────────────
    public List<Pet> getMyPetsForBooking(String username) {
        // 軟刪除的寵物不出現在預約下拉選單裡
        return petRepository.findByOwnerUsername(username).stream()
                .filter(pet -> !pet.isDeleted())
                .toList();
    }

    // ── 取得某筆預約的完整消費明細（供 LIFF「我的預約」點擊查看用）──────
    // 權限：本人（該預約的顧客）或店家/員工皆可查看
    // ── 契約副本——顧客（或店員）查看這筆預約簽署的契約紀錄 ──
    // 契約全文由前端載入 /liff/contract-body.html（同一版本），這裡只回傳簽署證明：
    // 版本、簽署時間、甲方簽名、乙方簽名檔、影像使用同意，以及雙方基本資料。
    public Map<String, Object> getContractRecord(Long appointmentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (!a.getUser().getId().equals(user.getId()) && !user.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：只能查看自己的契約");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointmentCode", "AP" + String.format("%03d", a.getId()));
        m.put("customerName", a.getUser().getName());
        m.put("petName", a.getPetName());
        m.put("serviceDate", a.getDate() != null ? a.getDate().toString() : null);
        m.put("contractVersion", a.getContractVersion() != null ? a.getContractVersion() : "2026-10-02 之前版本");
        m.put("currentVersion", ContractInfo.VERSION);
        m.put("agreedAt", a.getContractAgreedAt() != null ? a.getContractAgreedAt().toString() : null);
        m.put("customerSignature", a.getContractSignatureImage());
        m.put("photoShareConsent", a.isPhotoShareConsent());
        m.put("companySignature", companySignatureRepository.findFirstByOrderByIdDesc()
                .map(CompanySignature::getSignatureImage).orElse(null));
        return m;
    }

    public Optional<Appointment> findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId);
    }
}
