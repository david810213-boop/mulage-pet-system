package com.petgrooming.pet_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 需求 5：現場開單輸入
 * 需求 7-1 修正：允許純零售商品訂單（沒有任何美容服務項目，例如客人單純上門買東西），
 * 所以 items（美容服務項目）跟 retailItems（零售商品）都改成可為空，
 * 「至少要有一項（不管哪一種）」的驗證移到 Service 層做，
 * 因為這是跨兩個欄位的規則，單一欄位的 @NotEmpty 表達不出來。
 */
@Data
public class WalkInOrderCreateRequest {

    // 會員帳號（選填：現場客可能非會員）
    private String memberUsername;

    // 寵物名稱（現場填寫；純零售訂單可留空，不是每個買東西的客人都會帶寵物一起來）
    private String petName;

    // 訂單備註
    private String note;

    // 美容服務項目清單（可為空——純零售訂單沒有服務項目）
    private List<Item> items;

    // 零售商品清單（可為空——純服務訂單不用買東西）
    private List<RetailItem> retailItems;

    @Data
    public static class Item {
        // 以項目代碼帶入（對應 grooming_items.item_code）
        private String itemCode;
        // 經手人：實際員工帳號 id（選填；不填則為「未填寫」，之後從待補清單選人補上）
        private Long operatorStaffId;
    }

    @Data
    public static class RetailItem {
        private Long retailProductId;
        private int quantity;
    }
}
