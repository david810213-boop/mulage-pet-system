package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 需求 12：拆分歷史查詢的一列，記錄「誰拆給了誰、拆了多少」。
 */
@Data
@Builder
public class SplitHistoryResponse {
    private Long splitRecordId;    // 拆分產生的新紀錄 id
    private LocalDate serviceDate;
    private String petName;
    private String itemLabel;
    private String fromStaffName;  // 原本歸屬經手人
    private String toStaffName;    // 拆分後給誰
    private double halfPoints;     // 對半後各自積分
}
