package com.petgrooming.pet_system.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 需求（追加，2026-08-24）：Google 日曆串接——店家一個共用 Google 日曆帳號，
 * 預約「已確認」時自動同步一筆日曆事件，取消時自動刪除。
 *
 * 跟 CloudinaryService、LineMessagingService 用同一套「還沒設定好就優雅跳過」
 * 的原則：GOOGLE_CALENDAR_SERVICE_ACCOUNT_JSON_BASE64 或 GOOGLE_CALENDAR_ID
 * 沒設定的話，這個服務的所有方法都直接跳過、只記一行 log，不會拋例外、
 * 不會讓系統啟動失敗、更不會擋住預約本身的建立/確認/取消流程——日曆同步
 * 只是錦上添花的功能，絕對不能因為它失敗就讓顧客訂不到、店家排不進單。
 */
@Service
@Slf4j
public class GoogleCalendarService {

    @Value("${google.calendar.service-account-json-base64:}")
    private String serviceAccountJsonBase64;

    @Value("${google.calendar.calendar-id:}")
    private String calendarId;

    private Calendar calendarClient; // 延遲初始化，第一次真正用到才建立
    private boolean initAttempted = false;
    private boolean available = false;

    private synchronized boolean ensureInitialized() {
        if (initAttempted) return available;
        initAttempted = true;

        if (serviceAccountJsonBase64 == null || serviceAccountJsonBase64.isBlank()
                || calendarId == null || calendarId.isBlank()) {
            log.info("[Google日曆] 尚未設定 GOOGLE_CALENDAR_SERVICE_ACCOUNT_JSON_BASE64 / "
                    + "GOOGLE_CALENDAR_ID，日曆同步功能停用（不影響預約本身的所有功能）");
            return false;
        }

        try {
            byte[] jsonBytes = Base64.getDecoder().decode(serviceAccountJsonBase64);
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(jsonBytes))
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

            calendarClient = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Mulage Pet")
                    .build();

            available = true;
            log.info("[Google日曆] 初始化成功，日曆同步功能已啟用");
        } catch (Exception e) {
            // 憑證格式錯誤、Base64 解碼失敗、網路問題等，都在這裡攔下來，
            // 絕對不能讓這個初始化失敗往外傳，影響到呼叫端（AppointmentService）的正常運作。
            log.warn("[Google日曆] 初始化失敗，日曆同步功能停用：{}", e.getMessage());
            available = false;
        }
        return available;
    }

    /**
     * 建立或更新這筆預約對應的日曆事件。appointment.googleCalendarEventId
     * 已經有值（代表之前同步過）就用 update，否則建立新事件並把新的 eventId
     * 存回 appointment（呼叫端負責 save）。
     *
     * 任何錯誤都在這裡吞掉、記 log，不會往外拋——見類別註解的說明。
     */
    public void syncEvent(Appointment appointment) {
        if (!ensureInitialized()) return;

        try {
            var event = new com.google.api.services.calendar.model.Event();
            event.setSummary(buildTitle(appointment));
            event.setDescription(buildDescription(appointment));

            ZoneId zone = ZoneId.of("Asia/Taipei");
            var startDateTime = appointment.getDate().atTime(appointment.getStartTime()).atZone(zone);
            var endDateTime = appointment.getDate().atTime(appointment.getEndTime()).atZone(zone);

            event.setStart(new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(startDateTime.toInstant().toEpochMilli()))
                    .setTimeZone("Asia/Taipei"));
            event.setEnd(new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(endDateTime.toInstant().toEpochMilli()))
                    .setTimeZone("Asia/Taipei"));

            if (appointment.getGoogleCalendarEventId() == null) {
                var created = calendarClient.events().insert(calendarId, event).execute();
                appointment.setGoogleCalendarEventId(created.getId());
                log.info("[Google日曆] 已建立事件，預約 #{} → eventId {}", appointment.getId(), created.getId());
            } else {
                calendarClient.events().update(calendarId, appointment.getGoogleCalendarEventId(), event).execute();
                log.info("[Google日曆] 已更新事件，預約 #{}", appointment.getId());
            }
        } catch (Exception e) {
            log.warn("[Google日曆] 同步事件失敗（預約 #{} 仍正常，不受影響）：{}", appointment.getId(), e.getMessage());
        }
    }

    /** 取消預約時，把對應的日曆事件一併刪除。找不到/已經刪過也不當錯誤處理。 */
    public void deleteEvent(Appointment appointment) {
        if (!ensureInitialized()) return;
        if (appointment.getGoogleCalendarEventId() == null) return;

        try {
            calendarClient.events().delete(calendarId, appointment.getGoogleCalendarEventId()).execute();
            log.info("[Google日曆] 已刪除事件，預約 #{}", appointment.getId());
        } catch (Exception e) {
            log.warn("[Google日曆] 刪除事件失敗（預約 #{} 取消本身仍正常，不受影響）：{}",
                    appointment.getId(), e.getMessage());
        }
    }

    private String buildTitle(Appointment appointment) {
        String petTypeIcon = "DOG".equalsIgnoreCase(appointment.getPetType()) ? "🐶"
                : "CAT".equalsIgnoreCase(appointment.getPetType()) ? "🐱" : "🐾";
        return petTypeIcon + " " + appointment.getPetName() + "（" + appointment.getUser().getName() + "）";
    }

    private String buildDescription(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        if (appointment.getSelectedItems() != null && !appointment.getSelectedItems().isEmpty()) {
            String items = appointment.getSelectedItems().stream()
                    .map(GroomingItem::getName)
                    .collect(Collectors.joining("、"));
            sb.append("服務項目：").append(items).append("\n");
        }
        sb.append("金額：$").append(appointment.getTotalAmount()).append("\n");
        if (appointment.getUser().getPhone() != null) {
            sb.append("聯絡電話：").append(appointment.getUser().getPhone()).append("\n");
        }
        if (appointment.getInternalNote() != null && !appointment.getInternalNote().isBlank()) {
            sb.append("內部備注：").append(appointment.getInternalNote()).append("\n");
        }
        sb.append("（此事件由慕沐村預約系統自動同步）");
        return sb.toString();
    }
}
