package com.petgrooming.pet_system.enums;

import lombok.Getter;

// 預約狀態（與 paid 付款狀態分開紀錄；已取消的預約不會納入時段佔用計算）
@Getter
public enum AppointmentStatus {
    CONFIRMED("已確認"),
    CANCELLED("已取消"),
    PENDING_CONFIRM("待確認"),
    COMPLETED("已完成");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }
}
