package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.config.StoreRules;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.FinalCheckRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.LineMessagingService;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemComponentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 預約到店後的流程：到店開單、開始服務、結束服務（通知家長接回）、核對（接待送出）。
 */
@Service
@RequiredArgsConstructor
public class AppointmentCheckInService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final GroomingItemRepository groomingItemRepository; // 服務項目價格
    private final LineMessagingService lineMessagingService;
    private final PetGroomingNoteRepository petGroomingNoteRepository; // 進行中核對：毛孩美容狀況歷史
    private final GroomingNotePhotoService groomingNotePhotoService; // 核對照片
    private final PerformanceService performanceService; // 進行中核對：接待送出積分
    private final AppointmentItemRepository appointmentItemRepository; // 現場開單（依預約編號）
    private final AppointmentItemService appointmentItemService; // 訂單項目、經手人與積分
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 僅限既有客戶項目判斷
    private final GroomingItemComponentRepository groomingItemComponentRepository; // 套餐組成

    // ── 店員開始服務：CONFIRMED → IN_PROGRESS（寵物已到店開始施作）──────────
    @Transactional
    public AppointmentResponse startProgress(Long appointmentId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (appointment.isCancelled()) {
            throw new AppointmentException("此預約已取消，無法開始服務");
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new AppointmentException("僅「已確認」的預約可開始服務");
        }
        if (!appointment.isCheckinOrderConfirmed()) {
            throw new AppointmentException("請先依現場情況開立服務項目訂單，才能開始服務");
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
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new AppointmentException("僅「進行中」的預約可結束服務");
        }
        if (appointment.isServiceEndedDone()) {
            throw new AppointmentException("此預約已結束服務，請勿重複操作");
        }

        appointment.setServiceEndedDone(true);
        appointment.setServiceEndedStaff(staff);
        appointment.setServiceEndedAt(LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        // 誰點結束服務，「完成」積分就記給誰
        performanceService.addRecord(
                staff.getId(),
                appointment.getId(),
                PerformanceCategory.COMPLETE,
                PerformanceCategory.COMPLETE.getDefaultPoints(),
                appointment.getDate(),
                "結束服務：預約 #" + appointment.getId());

        // 用官方 LINE 通知家長可以來店接寵物了
        // 文案改版（店家指定新內容）。
        // 附註：裝飾符號改用 Java Unicode 逃逸序列（backslash + u + 4碼十六進位）寫死（純 ASCII），避免存檔/複製貼上
        // 過程中位元組被弄壞導致編譯失敗（踩過的坑，跟 WalkInOrderService 同一份模板）。
        // 逃逸序列對照的符號說明，請見交付 README，不要把實際符號貼進這個檔案。
        String notifyText = String.format(
                "【慕沐村 Mulage pet】%n" +
                        "\ud80c\udc83 \u2726 美容完成 \u2726 \ud80c\udc83%n" +
                        "%s的美容服務完成囉 \u025e%n" +
                        "再麻煩家長於 2 小時內%n" +
                        "前來接寶貝回家 \u2661",
                saved.getPetName());
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);

        return AppointmentResponse.from(saved);
    }

    // ── 現場開單（依預約編號）：家長到店後，店員依現場情況確認/調整服務項目 ──
    // 確認後才能開始服務；項目可先不指定經手人，之後從待補清單補上。
    @Transactional
    public AppointmentResponse confirmCheckinOrder(Long appointmentId, List<String> itemCodes, String username) {
        return confirmCheckinOrder(appointmentId, itemCodes, null, null, username);
    }

    @Transactional
    public AppointmentResponse confirmCheckinOrder(Long appointmentId, List<String> itemCodes,
            List<Long> operatorStaffIds, String username) {
        return confirmCheckinOrder(appointmentId, itemCodes, operatorStaffIds, null, username);
    }

    // 到店開單比照現場開單，每個項目可以直接選經手人。
    // operatorStaffIds 跟 itemCodes 一一對應（null／空字串＝稍後補填），選了的當下就寫入積分。
    @Transactional
    // operatorStaffIds2 是第二經手人（選填），同樣跟 itemCodes 一一對應
    public AppointmentResponse confirmCheckinOrder(Long appointmentId, List<String> itemCodes,
            List<Long> operatorStaffIds, List<Long> operatorStaffIds2, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (appointment.isCancelled()) {
            throw new AppointmentException("此預約已取消，無法開單");
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new AppointmentException("僅「已確認」的預約可開立現場服務項目訂單");
        }
        if (appointment.isCheckinOrderConfirmed()) {
            throw new AppointmentException("此預約已開立過現場服務項目訂單，請至「補項目經手人」調整");
        }
        if (itemCodes == null || itemCodes.isEmpty()) {
            throw new AppointmentException("請至少選擇一項服務項目");
        }

        int total = 0;
        for (int idx = 0; idx < itemCodes.size(); idx++) {
            String code = itemCodes.get(idx);
            Long operatorId = operatorStaffIds != null && idx < operatorStaffIds.size() ? operatorStaffIds.get(idx) : null;
            Long operatorId2 = operatorStaffIds2 != null && idx < operatorStaffIds2.size() ? operatorStaffIds2.get(idx) : null;
            GroomingItem gi = groomingItemRepository.findByItemCode(code)
                    .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到項目代碼：" + code));

            // 僅限既有客戶的項目，這隻寵物完全沒有消費紀錄的話不能選這個項目。
            if (gi.isRequiresExistingCustomer()
                    && !petConsumptionHistoryService.hasPriorPaidService(
                            appointment.getUser().getId(), appointment.getPetName(), appointmentId)) {
                throw new AppointmentException("「" + gi.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法選擇此項目");
            }

            // 主項目本身掛的積分分類（例如 BATH_CAT_S）如果有副組成，名稱後面比照副組成加上分類標籤
            // （例如「（洗（小貓））」），店家在待補經手人矩陣才看得出這行就是洗澡的積分。
            var itemComponents = groomingItemComponentRepository.findByGroomingItemId(gi.getId());
            String mainItemName = itemComponents.isEmpty()
                    ? gi.getName()
                    : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）";

            AppointmentItem item = AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .groomingItemId(gi.getId())
                    .itemName(mainItemName)
                    .price((int) Math.round(gi.getPrice()))
                    .points(gi.getPoints())
                    .performanceCategory(gi.getPerformanceCategory())
                    .build();
            appointmentItemService.assignOperators(item, operatorId, operatorId2);
            appointmentItemRepository.save(item);
            appointmentItemService.awardItemPoints(item); // 沒選經手人的會直接略過，之後從待補清單補
            appointmentItemService.expandPackageComponents(appointment, gi, itemComponents); // 套餐化——展開副組成
            total += item.getPrice();
        }

        appointment.setTotalAmount(total);
        appointment.setCheckinOrderConfirmed(true);
        Appointment saved = appointmentRepository.save(appointment);

        return AppointmentResponse.from(saved);
    }

    // ── 進行中核對（接待送出）───────────────────────────────────────────
    // 店員從「進行中」的預約選擇核對：記錄本次美容狀況備註（同步存入毛孩歷史），
    // 並取得家長現場簽名確認；完成後才可進入結帳，且將該店員記為「接待送出」積分。
    @Transactional
    public AppointmentResponse finalCheck(Long appointmentId, FinalCheckRequest req, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new AppointmentException("僅「進行中」的預約可進行核對");
        }
        if (!appointment.isServiceEndedDone()) {
            throw new AppointmentException("請先點擊「結束服務」，通知家長來店後才能進行核對");
        }
        if (appointment.isFinalCheckDone()) {
            throw new AppointmentException("此預約已完成核對，請勿重複操作");
        }

        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (note.isEmpty()) {
            throw new AppointmentException("請填寫本次毛孩美容狀況備註");
        }

        String signatureData = req.getSignatureData() == null ? "" : req.getSignatureData().trim();
        if (signatureData.isEmpty() || !signatureData.startsWith("data:image") || signatureData.length() < StoreRules.MIN_SIGNATURE_LENGTH) {
            throw new AppointmentException("請請家長於簽名板完成簽名確認");
        }

        // 核對照片（選填，最多 5 張），先驗證再開始寫資料
        List<String[]> photos = groomingNotePhotoService.normalize(req.getPhotoUrls(), req.getPhotoPublicIds());

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
            historyNote = petGroomingNoteRepository.save(historyNote);
            groomingNotePhotoService.attach(historyNote, photos);
        } else if (!photos.isEmpty()) {
            throw new AppointmentException(ErrorCode.NOT_FOUND, "找不到這隻毛孩的檔案，照片無法保存，請先移除照片再送出");
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
}
