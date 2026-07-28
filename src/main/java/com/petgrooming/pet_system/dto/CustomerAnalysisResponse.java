package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

// 後台「會員資料分析」頁面用：新客／回流客總覽 + 年齡、職業、居住區域、來源分布
@Data
@Builder
public class CustomerAnalysisResponse {

    private long totalCustomers;         // 總會員數（角色為 CUSTOMER）
    private long newCustomers;           // 新客：僅有 0～1 筆有效預約
    private long returningCustomers;     // 回流客：有 2 筆以上有效預約
    private long neverBookedCustomers;   // 尚未預約過的會員
    private long profileCompletedCount;  // 已完整填寫基本資料的會員數
    private double profileCompletionRate; // 完整填寫率（%）

    private Map<String, Long> ageBreakdown;        // 年齡區間分布
    private Map<String, Long> occupationBreakdown; // 職業分布
    private Map<String, Long> areaBreakdown;       // 居住區域分布
    private Map<String, Long> sourceBreakdown;     // 得知來源分布
}
