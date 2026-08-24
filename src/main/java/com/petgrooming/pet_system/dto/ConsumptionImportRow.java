package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 需求（追加）：既有消費紀錄批次匯入——CSV 一列代表一筆歷史消費。
 */
@Data
public class ConsumptionImportRow {
    private int rowNumber;
    private String phone;      // 對應到已匯入/已存在的會員（依電話比對）
    private String petName;    // 對應到該會員名下的毛孩（依名字比對）
    private String dateRaw;    // 消費日期，格式 yyyy-MM-dd
    private String amountRaw;  // 消費金額
    private String note;       // 備註（選填，例如「洗澡+剪毛」）
}
