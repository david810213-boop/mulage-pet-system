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

    // 定型化契約簽名：前端簽名板產生的手寫簽名圖片（base64 PNG dataURL）
    @jakarta.validation.constraints.NotBlank(message = "請詳閱定型化契約，並在簽名板上親筆簽名後再送出預約")
    private String contractSignatureData;
}