package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 需求 14：服務項目名稱/金額/描述自主修改。
 *
 * 跟 GroomingItemRequest（新增項目用）刻意分開成獨立的 DTO——最關鍵的差異是
 * **這裡完全沒有 itemCode 欄位**。代碼是項目的永久識別（歷史紀錄、績效統計、
 * 折扣規則都是用代碼對應，不是用名稱），一旦項目建立之後就不該再被改動。
 * 用「型別層級直接不存在這個欄位」來做唯讀保護，比「後端邏輯裡忽略某個欄位」
 * 更安全——不管前端表單怎麼被竄改、送了什麼欄位進來，這個 DTO 的結構本身
 * 就無法承載 itemCode，從根本上杜絕誤改代碼的可能性。
 */
@Data
public class UpdateGroomingItemRequest {

    @NotBlank(message = "美容項目名稱不能為空")
    private String name;

    private String description;

    @NotNull(message = "價格不能為空")
    @Positive(message = "價格必須大於 0")
    private Double price;

    // 需求 4：是否可線上預約
    private Boolean bookable = false;

    // 需求（追加）：積分分類，同 GroomingItemRequest 的說明——同一個績效分類底下
    // 現在有很多不同價格的項目，積分要能個別調整，不再完全綁死分類固定值。
    private Double points;

    // 需求（追加）：僅限既有客戶
    private Boolean requiresExistingCustomer = false;
}
