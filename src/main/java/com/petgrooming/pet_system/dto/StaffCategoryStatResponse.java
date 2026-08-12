package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 「積分管理」→ 依員工＋積分項目統計：某位員工某個月，
 * 每一種積分項目（例如「洗（大型）」）的總積分，以及換算成完成次數。
 * 換算方式：項目總積分 ÷ 該項目單次積分 = 完成次數
 * （例如洗大型犬單次 20 分，當月該項目累積 800 分，代表做了 40 隻）
 */
@Data
@Builder
public class StaffCategoryStatResponse {
    private String categoryLabel;   // 積分項目名稱，例如「洗（大型）」
    private double totalPoints;     // 該項目當月累積總積分
    private double unitPoints;      // 該項目單次積分
    private double completedCount;  // 換算後完成次數
}
