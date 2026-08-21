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
        return res;
    }
}