package com.petgrooming.pet_system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求 3：店家確認預約並敲定最後時間
 * confirmedTime 選填；未帶時預設為當下時間
 */
@Data
public class ConfirmAppointmentRequest {
    private LocalDateTime confirmedTime;
}
