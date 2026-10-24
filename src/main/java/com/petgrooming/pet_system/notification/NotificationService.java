package com.petgrooming.pet_system.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

// 預約通知（目前只寫 log，模擬發送；之後可接 Email / SMS）
@Slf4j
@Service
public class NotificationService {

    public void sendBookingConfirmation(String email, String petName,
                                        LocalDate date, LocalTime startTime) {
        String message = String.format(
            "【預約成功確認】您已成功預約 %s %s 的寵物美容服務。寵物：%s",
            date, startTime, petName
        );
        // 這裡模擬發送（之後可接 Email / SMS / LinePay SDK）；log 不記錄收件人帳號（個資）
        log.debug("[預約成功通知・模擬] {}", message);
    }

    public void scheduleReminder(String email, LocalDate date, LocalTime startTime) {
        LocalDate reminderDate = date.minusDays(1);
        String message = String.format(
            "提醒您：您的寵物美容預約在 %s %s，請準時到店。",
            date, startTime
        );
        log.debug("[預約提醒・模擬] 將於 {} 發送：{}", reminderDate, message);
    }
}
