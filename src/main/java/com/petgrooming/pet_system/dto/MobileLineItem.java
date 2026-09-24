package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 員工手機版核對／結帳／現場單明細共用的「一列項目」（需求，2026-09-24 第四批）。
 * 預約（AppointmentItem、AppointmentDetailResponse.DetailItem）跟現場單
 * （WalkInOrderResponse.ItemLine）欄位名稱各不相同，統一轉成這個格式後，
 * 核對、結帳兩個畫面可以共用同一份樣板。
 */
@Data
@Builder
public class MobileLineItem {
    private Long id;             // 可移除時才有值（AppointmentItem / WalkInOrderItem 的流水號）
    private String name;
    private int price;
    private boolean retail;      // 零售商品
    private String note;         // 項目下方的小字說明（例如「首次體驗 9 折」）
    private boolean noteOk;      // 說明是不是「有優惠」，用綠色顯示
    private String operator;     // 經手人姓名（現場單才有）
    private boolean operatorFilled;
}
