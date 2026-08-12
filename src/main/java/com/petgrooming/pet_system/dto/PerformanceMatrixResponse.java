package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 「積分項目統計」矩陣頁面用：全部員工 × 全部積分項目的統計結果。
 */
@Data
@Builder
public class PerformanceMatrixResponse {
    private List<PerformanceCategory> categories; // 欄位順序（依 enum 宣告順序，排除 OTHER）
    private List<StaffMatrixRow> rows;             // 每位員工一列
    private StaffMatrixRow totalsRow;              // 最下方「合計」列（每個項目加總）
    private double grandTotal;                     // 全體員工總積分加總
}
