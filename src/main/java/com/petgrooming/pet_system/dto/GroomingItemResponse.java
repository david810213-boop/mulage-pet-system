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

    // 靜態工廠：將 Entity 映射成 DTO
    public static GroomingItemResponse from(GroomingItem item) {
        GroomingItemResponse res = new GroomingItemResponse();
        res.setId(item.getId());
        res.setItemCode(item.getItemCode());
        res.setName(item.getName());
        res.setDescription(item.getDescription());
        res.setPrice(item.getPrice());
        res.setBookable(item.isBookable());
        return res;
    }
}