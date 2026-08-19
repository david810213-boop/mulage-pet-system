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
    private int totalAmount;      // 帳面總額（未打折）
    private Integer chargedAmount; // 需求 5：實際扣款金額（有打折的話會比 totalAmount 少；null 代表跟 totalAmount 相同或尚未結帳）
    private boolean paid;
    private String paymentMethodLabel;
    private LocalDateTime paymentTime;
    private String handledBy;

    @Data
    @Builder
    public static class DetailItem {
        private Long itemId; // 需求（追加）：現場加購的零售商品需要能個別移除，其餘（selectedItems 舊資料）為 null
        private String name;
        private int price;
        private String operatorName; // 現場開單項目才有經手人；一般預約項目則為 null
        private boolean discountEligible; // 需求 5：這個項目是否可享會員折扣（僅代表項目本身的靜態設定，不代表本次是否真的有打折）
        // 需求 8-1 修正：90天回洗優惠與會員折扣只能擇一，消費明細要能看出「實際套用的是哪一種」
        private boolean rewashEligible; // 這個項目是否符合回洗優惠資格（貓咪洗澡 + 距上次洗澡未滿90天）
        private boolean retailItem; // 需求（追加）：是否為結帳頁加購的零售商品（不打折、不算回洗優惠）
        private com.petgrooming.pet_system.enums.DiscountType appliedDiscountType; // 已結帳的預約：實際套用的折扣種類；未結帳（尚未選付款方式）則為 null
    }
}