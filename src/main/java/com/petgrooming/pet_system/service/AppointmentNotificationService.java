package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.NotificationService;
import com.petgrooming.pet_system.notification.LineMessagingService;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 預約的 LINE 通知：新預約通知店家與員工、預約確認通知、前一天提醒（每天 19:00 排程）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentNotificationService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LineMessagingService lineMessagingService;
    private final GoogleCalendarService googleCalendarService; // Google 日曆串接

    /**
     * 新預約通知所有已綁定 LINE 的店家與員工。當天預約已直接確認，立即通知顧客並同步 Google 日曆
     * （待確認的預約要等店家確認時間後，由 confirm() 通知與同步，避免日曆被還會變動的時段佔用）。
     */
    void notifyBooked(Appointment saved, User user, Pet pet, AppointmentRequest req) {
        notifyStaffNewBooking(saved);
        notificationService.sendBookingConfirmation(user.getUsername(), pet.getName(), req.getDate(), req.getStartTime());
        notificationService.scheduleReminder(user.getUsername(), req.getDate(), req.getStartTime());
        if (saved.getStatus() == AppointmentStatus.CONFIRMED) {
            sendConfirmedNotify(saved);
            googleCalendarService.syncEvent(saved);
            appointmentRepository.save(saved); // 同步成功時會寫入 googleCalendarEventId
        }
    }

    // 新預約送出時通知店家/員工，跟 StoreSupplyService
    // 低庫存通知同一套「發給所有已綁定 LINE 的員工/店家帳號」機制，不用另外設計。
    // 推播失敗（token 沒設定、帳號沒綁 LINE 等）不應該讓預約本身失敗，
    // pushText() 內部已經處理過這個容錯，這裡不用再包 try-catch。
    private void notifyStaffNewBooking(Appointment appointment) {
        String statusText = appointment.getStatus() == AppointmentStatus.CONFIRMED
                ? "（當天預約，已自動確認）" : "（待確認）";
        String text = String.format(
                "\uD83D\uDCC5【新預約通知】%s%s\n毛孩：%s（%s）\n時間：%s %s~%s\n顧客：%s\n金額：%s",
                appointment.getPetName(), statusText,
                appointment.getPetName(), appointment.getPetType(),
                appointment.getDate(), appointment.getStartTime(), appointment.getEndTime(),
                appointment.getUser().getName(),
                appointment.isFirstVisitAssessment() && !appointment.isCheckinOrderConfirmed()
                        ? "初次・現場評估（到店後開單報價）"
                        : "$" + appointment.getTotalAmount());

        List<User> staffAndAdmin = new ArrayList<>(userRepository.findByRole(UserRole.STAFF));
        staffAndAdmin.addAll(userRepository.findByRole(UserRole.ADMIN));
        for (User u : staffAndAdmin) {
            if (u.getLineUserId() != null && !u.getLineUserId().isBlank()) {
                lineMessagingService.pushText(u.getLineUserId(), text);
            }
        }
    }


    // 預約確認通知文字內容 + 發送，供「店家點確認」與「當日臨時預約自動確認」共用
    // 文案改版（店家指定新內容），改成完整的預約須知格式。
    // 附註：裝飾符號改用 Java Unicode 逃逸序列（backslash + u + 4碼十六進位）寫死（純 ASCII），避免這些生僻 Unicode
    // 符號在存檔/複製貼上過程中位元組被弄壞、導致編譯失敗（踩過的坑：
    // "illegal start of expression"）。中文字本身沒有這個風險，維持原樣。
    // 註解裡故意不放實際符號本身（連放在註解裡都曾經導致編譯錯誤），
    // 逃逸序列對照的符號說明，請見交付 README，不要再貼進這個檔案。
    void sendConfirmedNotify(Appointment saved) {
        String dateStr = saved.getDate().format(DateTimeFormatter.ofPattern("M/d"));
        String notifyText = String.format(
                "【慕沐村 Mulage pet】%n" +
                        "\u3014\u2713 預約確認\u3015%n" +
                        "%s  %s%n" +
                        "%s的美容預約已確認 \u2661%n%n" +
                        "\u23f0 預約提醒%n" +
                        "預約前一天將再次傳送訊息提醒您%n%n" +
                        "\ud80c\udc83 預約時間%n" +
                        "\u30fb狗狗最早可於預約時間前 30 分鐘抵達%n" +
                        "\u30fb遲到超過 20 分鐘，將自動取消當日預約，並視同臨時取消%n" +
                        "\u30fb本店採全預約制，如需改期請提前告知%n%n" +
                        "\u2302 接回時間%n" +
                        "美容完成後請於 2 小時內接回%n" +
                        "逾時將酌收一次性延遲服務費$200%n" +
                        "如有特殊情況，請提前與我們詢問\u025e%n%n" +
                        "\u0b68\u0b67 取消／異動%n" +
                        "預約前 24 小時內臨時取消，下次預約需支付50%% 訂金；未赴約訂金恕不退還。%n" +
                        "會員臨時取消，當次取消費用（美容費用30%%）將直接由儲值金扣除。%n%n" +
                        "\ud83d\udc3e 美容小提醒%n" +
                        "\u30fb腳底毛皆採平剃，如有特殊需求請提前告知%n" +
                        "\u30fb為確保毛孩安全，現場採門禁管理，恕不開放家長等候%n%n" +
                        "\u2726 營業時間%n" +
                        "最後接狗／貓時間為 19:30%n%n" +
                        "感謝家長的配合與理解%n" +
                        "期待與您和寶貝們相見\u2661",
                dateStr, saved.getStartTime(), saved.getPetName());
        lineMessagingService.pushText(saved.getUser().getLineUserId(), notifyText);
    }

    // ── 每天 19:00 自動掃描「隔天」預約，發送前一日提醒 ─────────────
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

    // 前一天預約提醒文案改版（店家指定新內容），
    // 補回寵物名字（原始文案沒寫，店家確認要保留）。
    // 原本這裡有一段「隔天預約時間早於 11:00 要額外提醒準時到場」的邏輯
    // （EARLY_SLOT_CUTOFF），但店家開門時間本來就是 11:00，條件永遠不會成立，
    // 是早就存在的死代碼，新範本也沒有適合放這句提醒的位置，順手一併拿掉。
    // 附註：裝飾符號改用 Java Unicode 逃逸序列（backslash + u + 4碼十六進位）寫死（純 ASCII），避免存檔/複製貼上
    // 過程中位元組被弄壞導致編譯失敗（踩過的坑）。逃逸序列對照的符號說明，
    // 請見交付 README，不要把實際符號貼進這個檔案（連放在註解裡都會出問題）。
    private String buildReminderText(Appointment appointment) {
        return String.format(
                "【慕沐村 Mulage pet】%n" +
                        "\ud83d\udd14預約提醒 \ud83d\udd14%n" +
                        "***明天 %s %s%n" +
                        "%s有預約洗香香 \ud80c\ude12\ud80c\udff8%n" +
                        "\ud83d\ude97開車前來的家長%n" +
                        "可於社區大門外短暫停靠接送%n" +
                        "請勿停放於「車道出入口」%n" +
                        "明天見 \u029a",
                appointment.getDate(), appointment.getStartTime(), appointment.getPetName());
    }
}
