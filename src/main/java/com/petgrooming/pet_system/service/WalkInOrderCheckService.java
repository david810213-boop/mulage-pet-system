package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.notification.LineMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 現場單的服務流程（比照預約單）：結束服務（完成積分）、核對（美容狀況備註、簽名、照片，會員單同步寫入毛孩美容紀錄）。
 */
@Service
@RequiredArgsConstructor
public class WalkInOrderCheckService {

    private final WalkInOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;
    private final LineMessagingService lineMessagingService;
    private final PetRepository petRepository; // 需求（追加）：查現場單寵物物種
    private final PetGroomingNoteRepository petGroomingNoteRepository; // 需求（2026-09-29）：現場單核對也寫入美容紀錄
    private final GroomingNotePhotoService groomingNotePhotoService; // 需求（2026-09-29）：核對照片

    // ── 結束服務：服務項目全部做完（比照預約單邏輯）─────────────────────
    // 狀態不影響 paid，只記錄「誰結束的」+ 完成積分，並強制之後的核對必須先做這一步。
    @Transactional
    public WalkInOrderResponse endService(Long orderId, String username) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));

        if (order.isPaid()) {
            throw new PaymentException("此單已結帳，無法結束服務");
        }
        if (order.isServiceEndedDone()) {
            throw new PaymentException("此單已結束服務，請勿重複操作");
        }

        order.setServiceEndedDone(true);
        order.setServiceEndedStaff(staff);
        order.setServiceEndedAt(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        performanceService.addWalkInRecord(
                staff.getId(),
                order.getId(),
                PerformanceCategory.COMPLETE,
                PerformanceCategory.COMPLETE.getDefaultPoints(),
                LocalDate.now(),
                "結束服務：現場單 #" + order.getId());

        // 若這張單有綁定會員，通知家長可以來接寵物了
        // 需求（追加，2026-08-30）：文案改版（店家指定新內容），跟 AppointmentService
        // 同一份模板同步更新，避免只改一處造成兩個通知來源文字不一致。
        // 附註：裝飾符號改用 Java Unicode 逃逸序列（backslash + u + 4碼十六進位）寫死（純 ASCII），避免這幾個生僻
        // Unicode 符號在存檔/複製貼上過程中位元組被弄壞，導致編譯器解析失敗
        // （之前踩過的坑：曾經因為這個原因報 "illegal start of expression"）。
        // 逃逸序列對照的符號說明，請見交付 README，不要把實際符號貼進這個檔案。
        if (saved.getMember() != null) {
            String notifyText = String.format(
                    "【慕沐村 Mulage pet】%n" +
                            "\ud80c\udc83 \u2726 美容完成 \u2726 \ud80c\udc83%n" +
                            "%s的美容服務完成囉 \u025e%n" +
                            "再麻煩家長於 2 小時內%n" +
                            "前來接寶貝回家 \u2661",
                    saved.getPetName());
            lineMessagingService.pushText(saved.getMember().getLineUserId(), notifyText);
        }

        return WalkInOrderResponse.from(saved);
    }

    // ── 核對：填美容狀況備註 + 簽名確認（比照預約單邏輯）───────────────
    // 須先完成結束服務才能核對；核對完成才能結帳。
    @Transactional
    public WalkInOrderResponse finalCheck(Long orderId, String note, String signatureData, String username) {
        return finalCheck(orderId, note, signatureData, username, null, null);
    }

    // 需求（2026-09-29）：核對時可附上美容狀況照片（最多 5 張）。會員單會把備註＋照片
    // 寫進毛孩的美容紀錄（跟預約單一樣），顧客在 LIFF「我的毛孩」看得到；
    // 非會員單沒有毛孩檔案，不能附照片。
    @Transactional
    public WalkInOrderResponse finalCheck(Long orderId, String note, String signatureData, String username,
            List<String> photoUrls, List<String> photoPublicIds) {
        User staff = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        if (!staff.isStaffOrAdmin()) {
            throw new PaymentException(ErrorCode.FORBIDDEN, "權限不足：僅店家 / 員工可操作");
        }

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));

        if (!order.isServiceEndedDone()) {
            throw new PaymentException("請先點擊「結束服務」才能進行核對");
        }
        if (order.isFinalCheckDone()) {
            throw new PaymentException("此單已完成核對，請勿重複操作");
        }

        String noteTrim = note == null ? "" : note.trim();
        if (noteTrim.isEmpty()) {
            throw new PaymentException("請填寫本次美容狀況備註");
        }
        String sigTrim = signatureData == null ? "" : signatureData.trim();
        if (sigTrim.isEmpty() || !sigTrim.startsWith("data:image") || sigTrim.length() < 1000) {
            throw new PaymentException("請請家長於簽名板完成簽名確認");
        }

        List<String[]> photos = groomingNotePhotoService.normalize(photoUrls, photoPublicIds);
        Pet pet = order.getMember() == null ? null
                : petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                        .orElse(null);
        if (pet == null && !photos.isEmpty()) {
            throw new PaymentException("非會員單或找不到毛孩檔案，照片無法保存，請先移除照片再送出");
        }

        order.setFinalCheckDone(true);
        order.setFinalCheckStaff(staff);
        order.setFinalCheckNote(noteTrim);
        order.setFinalCheckSignatureImage(sigTrim);
        order.setFinalCheckAt(LocalDateTime.now());
        WalkInOrder saved = orderRepository.save(order);

        // 需求（2026-09-29）：會員單的核對備註＋照片也寫進毛孩美容紀錄
        if (pet != null) {
            PetGroomingNote historyNote = petGroomingNoteRepository.save(
                    PetGroomingNote.builder()
                            .pet(pet)
                            .walkInOrderId(order.getId())
                            .staff(staff)
                            .note(noteTrim)
                            .serviceDate(LocalDate.now())
                            .build());
            groomingNotePhotoService.attach(historyNote, photos);
        }

        performanceService.addWalkInRecord(
                staff.getId(),
                order.getId(),
                PerformanceCategory.CHECKOUT,
                PerformanceCategory.CHECKOUT.getDefaultPoints(),
                LocalDate.now(),
                "接待送出核對：現場單 #" + order.getId());

        return WalkInOrderResponse.from(saved);
    }
}
