package com.petgrooming.pet_system.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

// 把原本的 NotificationHandler + ReminderService 整合成一個 Spring Bean
// 原本的 Observer 邏輯完整保留，只是改成 @Service 讓 Spring 管理
@Slf4j
@Service
public class NotificationService {

    // 原本 Systemtest 裡 new EmailNotifier() / SMSNotifier() / LineNotifier() 的動作
    // 改成在這裡初始化，之後從 application.yml 設定要啟用哪些管道
    public void sendBookingConfirmation(String email, String petName,
                                        LocalDate date, LocalTime startTime) {
        String message = String.format(
            "【預約成功確認】您已成功預約 %s %s 的寵物美容服務。寵物：%s",
            date, startTime, petName
        );
        // 這裡模擬發送（之後可接 Email / SMS / LinePay SDK）；log 不記錄收件人帳號（個資）
        log.debug("[預約成功通知・模擬] {}", message);
    }

    // 對應原本 ReminderService.scheduleReminder()
    public void scheduleReminder(String email, LocalDate date, LocalTime startTime) {
        LocalDate reminderDate = date.minusDays(1);
        String message = String.format(
            "提醒您：您的寵物美容預約在 %s %s，請準時到店。",
            date, startTime
        );
        log.debug("[預約提醒・模擬] 將於 {} 發送：{}", reminderDate, message);
    }
}
