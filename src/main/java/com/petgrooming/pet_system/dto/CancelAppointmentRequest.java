package com.petgrooming.pet_system.dto;

import lombok.Data;

// 取消預約請求
@Data
public class CancelAppointmentRequest {
    private String reason; // 取消原因（選填）
}
