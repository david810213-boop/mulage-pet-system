package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 需求 5：現場開單輸入
 */
@Data
public class WalkInOrderCreateRequest {

    // 會員帳號（選填：現場客可能非會員）
    private String memberUsername;

    // 寵物名稱（現場填寫）
    private String petName;

    // 訂單備註
    private String note;

    // 項目清單（至少一項）
    @NotEmpty(message = "請至少加入一個項目")
    private List<Item> items;

    @Data
    public static class Item {
        // 以項目代碼帶入（對應 grooming_items.item_code）
        private String itemCode;
        // 經手人：實際員工帳號 id（選填；不填則為「未填寫」，之後從待補清單選人補上）
        private Long operatorStaffId;
    }
}
