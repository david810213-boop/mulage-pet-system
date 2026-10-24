package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class AppointmentRequest {

    @NotNull(message = "請選擇寵物")
    private Long petId;
    @Schema(description = "預約日期", example = "2026-11-02")
    private LocalDate date;
    @Schema(description = "開始時間（須對齊時段格線）", example = "12:00")
    private LocalTime startTime;
    @Schema(description = "結束時間", example = "14:00")
    private LocalTime endTime;
    @Schema(description = "選擇的服務項目代碼；初次預約可不選，由店家到店評估")
    private List<String> selectedItems;

    @Schema(description = "負責美容的員工 id（選填，由店家後台指派；結帳後自動計算績效用）")
    private Long staffId;

    @Schema(description = "定型化契約簽名：簽名板產生的手寫簽名圖片（base64 PNG data URL）")
    @NotBlank(message = "請詳閱定型化契約，並在簽名板上親筆簽名後再送出預約")
    private String contractSignatureData;

    @Schema(description = "契約第二十一條：是否同意分享美容照片（未勾＝不同意）")
    private Boolean photoShareConsent;
}