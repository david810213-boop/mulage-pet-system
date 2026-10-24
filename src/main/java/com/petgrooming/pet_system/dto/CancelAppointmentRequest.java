package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 取消預約請求
@Data
public class CancelAppointmentRequest {
    @Schema(description = "取消原因（選填）")
    private String reason;
}
