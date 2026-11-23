package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.DepositPayment;
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
    void notifyBooked(Appointment saved, User user, Pet pet, AppointmentRequest req, int depositDue) {
        notifyStaffNewBooking(saved, depositDue);
        if (saved.isAwaitingDeposit()) {
            sendDepositRequest(saved, depositDue);
        }
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
    private void notifyStaffNewBooking(Appointment appointment, int depositDue) {
        String statusText = appointment.isAwaitingDeposit() ? "（待付訂金 $" + depositDue + "）"
                : appointment.getStatus() == AppointmentStatus.CONFIRMED ? "（當天預約，已自動確認）" : "（待確認）";
        String text = String.format(
                "\uD83D\uDCC5【新預約通知】%s%s\n毛孩：%s（%s）\n時間：%s %s~%s\n顧客：%s\n金額：%s",
                appointment.getPetName(), statusText,
                appointment.getPetName(), appointment.getPetType(),
                appointment.getDate(), appointment.getStartTime(), appointment.getEndTime(),
                appointment.getUser().getName(),
                appointment.isFirstVisitAssessment() && !appointment.isCheckinOrderConfirmed()
                        ? "初次・現場評估（到店後開單報價）"
                        : "$" + appointment.getTotalAmount());

        pushToStaff(text);
    }

    /** 發給所有已綁定 LINE 的員工與店家帳號。 */
    private void pushToStaff(String text) {
        List<User> staffAndAdmin = new ArrayList<>(userRepository.findByRole(UserRole.STAFF));
        staffAndAdmin.addAll(userRepository.findByRole(UserRole.ADMIN));
        for (User u : staffAndAdmin) {
            if (!u.isActiveAccount() || u.isPartTime()) {
                continue;
            }
            if (u.getLineUserId() != null && !u.getLineUserId().isBlank()) {
                lineMessagingService.pushText(u.getLineUserId(), text);
            }
        }
    }

    // ── 取消、改期 ────────────────────────────────────────────────────

    /** 顧客在 LINE 自行取消時通知店家，避免漏看（店家取消不用通知自己）。 */
    void notifyStaffCustomerCancelled(Appointment a) {
        pushToStaff(String.format("【顧客取消預約】%s%n時間：%s %s~%s%n顧客：%s%n原因：%s",
                a.getPetName(), a.getDate(), a.getStartTime(), a.getEndTime(), a.getUser().getName(),
                a.getCancelReason() == null || a.getCancelReason().isBlank() ? "未填寫" : a.getCancelReason()));
    }

    void sendRescheduled(Appointment a) {
        lineMessagingService.pushText(a.getUser().getLineUserId(), String.format(
                "【慕沐村 Mulage pet】%n%s的預約已改期為 %s %s%n已付的訂金將保留至新的預約日期。",
                a.getPetName(), a.getDate().format(DateTimeFormatter.ofPattern("M/d")), a.getStartTime()));
    }

    // ── 預約訂金 ──────────────────────────────────────────────────────

    private static final DateTimeFormatter DUE_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    void sendDepositRequest(Appointment a, int amount) {
        lineMessagingService.pushText(a.getUser().getLineUserId(), String.format(
                "【慕沐村 Mulage pet】%n" +
                        "已收到%s %s %s 的預約申請。%n" +
                        "依預約須知，本次預約需預付訂金 NT$%d，%n" +
                        "請於 %s 前完成匯款，並在「我的預約」填寫匯款帳號後五碼。%n" +
                        "確認收到訂金後，預約即成立；逾期未付款，預約將自動取消。%n" +
                        "訂金將於當日服務結帳時折抵消費金額。",
                a.getPetName(), a.getDate().format(DateTimeFormatter.ofPattern("M/d")), a.getStartTime(),
                amount, a.getDepositDueAt() == null ? "期限" : a.getDepositDueAt().format(DUE_FMT)));
    }

    void notifyStaffDepositSubmitted(DepositPayment p) {
        pushToStaff(String.format("【訂金待核帳】%s%n預約日期：%s%n金額：$%d%n匯款後五碼：%s",
                p.getUser().getName(), p.getAppointmentDate(), p.getAmount(), p.getLastFiveDigits()));
    }

    void sendDepositRejected(DepositPayment p) {
        lineMessagingService.pushText(p.getUser().getLineUserId(), String.format(
                "【慕沐村 Mulage pet】%n%s 預約的訂金匯款核對未通過%s。%n請確認匯款資訊後重新填寫，或聯繫官方 LINE 客服。",
                p.getAppointmentDate().format(DateTimeFormatter.ofPattern("M/d")),
                p.getRejectReason() == null ? "" : "：" + p.getRejectReason()));
    }

    void sendDepositExpired(Appointment a) {
        lineMessagingService.pushText(a.getUser().getLineUserId(), String.format(
                "【慕沐村 Mulage pet】%n%s %s %s 的預約因未於期限內收到訂金，已自動取消。%n如仍需預約，歡迎重新預約或聯繫官方 LINE 客服。",
                a.getPetName(), a.getDate().format(DateTimeFormatter.ofPattern("M/d")), a.getStartTime()));
        pushToStaff(String.format("【逾期未付訂金，已自動取消】%s%n時間：%s %s%n顧客：%s",
                a.getPetName(), a.getDate(), a.getStartTime(), a.getUser().getName()));
    }


    // 預約確認通知（文案由店家指定），供「店家點確認」與「當日臨時預約自動確認」共用。
    // 裝飾符號一律用 Java Unicode 逃逸序列寫（見 CLAUDE.md 第四節），不要直接貼符號，連註解裡也不要放。
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
                        "已付訂金的預約，預約前72小時以上取消退還全額、72小時內退還50%%、24小時內恕不退還；未赴約訂金恕不退還。%n" +
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

    // 前一天預約提醒（文案由店家指定，含寵物名字）。
    // 裝飾符號一律用 Java Unicode 逃逸序列寫（見 CLAUDE.md 第四節），不要直接貼符號，連註解裡也不要放。
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
