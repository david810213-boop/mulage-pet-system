package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 需求（追加）：既有會員資料批次匯入——CSV 一列代表一隻毛孩，同一個電話號碼
 * 底下可以有好幾列（一位家長多隻毛孩），匯入時會依電話號碼分組，同一組只
 * 建立一筆會員帳號，底下掛多筆寵物。
 */
@Data
public class MemberImportRow {
    private int rowNumber;       // 第幾列（從 1 開始算，方便錯誤訊息定位，不含表頭那行）
    private String ownerName;    // 家長姓名
    private String phone;        // 家長電話
    private String petName;      // 毛孩名字
    private String petTypeRaw;   // 物種原始文字（貓/狗/其他，或 CAT/DOG/OTHER）
    private String breed;        // 品種
    private String weightRaw;    // 體重原始文字
    private String ageRaw;       // 年齡原始文字
    private String separationAnxietyRaw; // 是否分離焦慮原始文字（是/否、Y/N）
    private String notes;        // 注意事項
}
