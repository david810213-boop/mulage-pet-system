package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 需求 12：待拆分積分清單的一列，補上寵物名、服務項目名稱，方便快速核對。
 */
@Data
@Builder
public class SplitCandidateResponse {
    private Long id;
    private LocalDate serviceDate;
    private String petName;
    private String itemLabel;      // 服務項目（積分分類名稱）
    private String staffName;      // 目前歸屬的經手人
    private Long staffId;
    private double points;         // 原始積分
    private double halfPoints;     // 對半後每人各得幾分（小數點第一位）
}
