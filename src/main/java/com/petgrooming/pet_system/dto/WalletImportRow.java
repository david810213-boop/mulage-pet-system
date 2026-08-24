package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 需求（追加）：既有儲值餘額批次匯入——CSV 一列代表一位會員目前的儲值餘額。
 */
@Data
public class WalletImportRow {
    private int rowNumber;
    private String phone;      // 對應到已匯入/已存在的會員（依電話比對）
    private String balanceRaw; // 儲值餘額（元）
}
