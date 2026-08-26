package com.petgrooming.pet_system.dto;



import com.petgrooming.pet_system.model.GroomingItem;

import lombok.Data;

@Data
public class GroomingItemResponse {
    private Long id;
    private String itemCode;
    private String name;
    private String description;
    private Double price;
    private boolean bookable;   // 需求 4：是否可線上預約
    private String performanceCategory; // 需求 4：積分分類名稱（例如 OTHER / SPECIAL），供前端判斷是否為特殊/不計分項目
    private Double points; // 需求（追加）：這個項目實際的積分（已考慮個別覆寫，不是分類預設值）
    private boolean requiresExistingCustomer; // 需求（追加）：僅限既有客戶
    private String applicablePetType; // 需求（追加）：適用物種（DOG/CAT/null=兩者皆可）
    private String catCoatCategory; // 需求（追加）：貓咪毛髮分類（SINGLE_LAYER/DOUBLE_LAYER/LONG_HAIR/null=與毛髮分類無關）
    private String dogWeightTier; // 需求（追加）：狗狗體重級距（SMALL~EXTRA_LARGE/null=與體重級距無關）
    // 需求（追加，2026-08-26）：假日限定套餐——這個項目是不是「完整套餐」
    // （有副組成的項目才算），由 GroomingServiceImpl 組裝時額外查好塞進來，
    // 不是從 GroomingItem entity 直接映射（entity 本身沒有這個概念，是從
    // 「有沒有副組成」這個既有訊號動態算出來的）。
    private Boolean isPackage;

    // 靜態工廠：將 Entity 映射成 DTO
    public static GroomingItemResponse from(GroomingItem item) {
        GroomingItemResponse res = new GroomingItemResponse();
        res.setId(item.getId());
        res.setItemCode(item.getItemCode());
        res.setName(item.getName());
        res.setDescription(item.getDescription());
        res.setPrice(item.getPrice());
        res.setBookable(item.isBookable());
        res.setPerformanceCategory(item.getPerformanceCategory() != null ? item.getPerformanceCategory().name() : null);
        res.setPoints(item.getPoints());
        res.setRequiresExistingCustomer(item.isRequiresExistingCustomer());
        res.setApplicablePetType(item.getApplicablePetType() != null ? item.getApplicablePetType().name() : null);
        res.setCatCoatCategory(item.getCatCoatCategory() != null ? item.getCatCoatCategory().name() : null);
        res.setDogWeightTier(item.getDogWeightTier() != null ? item.getDogWeightTier().name() : null);
        return res;
    }
}