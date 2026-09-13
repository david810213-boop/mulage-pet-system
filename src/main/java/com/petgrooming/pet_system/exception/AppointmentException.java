package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 預約相關業務錯誤：建立/取消/確認預約、時段容量、營業時間、
 * 限定物種、公休日判斷等。用在 AppointmentService。
 *
 * ⚠️ 目前 AppointmentService 尚未遷移到這個例外類別（throw 的地方有 48 處，
 * 這次先不動，避免大範圍改動一次做不完整），先建立類別讓其他已遷移的
 * service 可以使用，AppointmentService 之後再逐步遷移。
 */
public class AppointmentException extends BusinessException {
    public AppointmentException(String message) {
        super(message);
    }

    public AppointmentException(String message, HttpStatus status) {
        super(message, status);
    }
}
