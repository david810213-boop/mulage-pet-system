package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SimpleImportResult {
    private int totalRows;
    private int succeeded;
    private List<String> rowErrors; // 「第幾列：原因」，包含「找不到這個電話對應的會員，請先匯入會員資料」這類錯誤
}
