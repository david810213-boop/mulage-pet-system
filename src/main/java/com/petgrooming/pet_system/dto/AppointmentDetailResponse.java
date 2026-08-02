package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * LIFF「我的預約」頁面點擊某筆預約後顯示的完整消費明細。
 * 服務項目優先顯示現場開單（AppointmentItem）的實際明細（有經手人資訊），
 * 若這筆預約沒有走過現場開單流程（例如舊資料），才退回顯示原本預約時勾選的項目。
 */
@Data
@Builder
public class AppointmentDetailResponse {
    private String appointmentCode;
    private String petName;
    private String petType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String statusLabel;
    private boolean cancelled;
    private String memberNote;
    private List<DetailItem> items;
    private int totalAmount;
    private boolean paid;
    private String paymentMethodLabel;
    private LocalDateTime paymentTime;
    private String handledBy;

    @Data
    @Builder
    public static class DetailItem {
        private String name;
        private int price;
        private String operatorName; // 現場開單項目才有經手人；一般預約項目則為 null
    }
}