package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class AppointmentRequest {

    @NotNull(message = "請選擇寵物")
    private Long petId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<String> selectedItems;

    // 負責美容的員工（選填，由店家後台指派；結帳後自動計算績效用）
    private Long staffId;
}