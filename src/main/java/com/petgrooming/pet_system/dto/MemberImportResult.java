package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MemberImportResult {
    private int totalRows;          // CSV 總共幾列（不含表頭）
    private int membersCreated;     // 新建立幾筆會員帳號
    private int petsCreated;        // 新建立幾筆寵物
    private int membersSkipped;     // 電話號碼已存在，整組跳過的會員數
    private List<String> skippedPhones;  // 被跳過的電話號碼清單（方便店家對照原始名冊）
    private List<String> rowErrors; // 格式錯誤的列（第幾列＋原因），這些列完全不會被匯入
}
