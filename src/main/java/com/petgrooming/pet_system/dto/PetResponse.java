package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Pet;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PetResponse {
    private Long id;
    private String name;
    private PetType petType;
    private String petTypeLabel;
    private String breed;
    private Double weight;
    private Integer age;
    private String ownerName;

    // 新增欄位
    private PetSizeCategory sizeCategory;       // 體型（系統自動判斷）
    private String sizeCategoryLabel;           // 體型中文顯示
    private CoatType coatType;                  // 毛長
    private String coatTypeLabel;               // 毛長中文
    private Boolean hasSeparationAnxiety;       // 分離焦慮
    private String ownerPhone;                  // 家長手機
    private String notes;                       // 注意事項
    private String photoUrl;                    // 需求 17：寵物照片（大頭照）

    // 系統根據體型自動推薦的服務 itemCode（預約時直接帶入）
    private List<String> recommendedItemCodes;

    // 需求（追加）：這隻寵物是不是既有客戶（有沒有任何一筆已結帳消費紀錄），
    // 供預約表單 JS 判斷「僅限既有客戶」的項目要不要顯示在選單裡。
    private Boolean isExistingCustomer;

    public static PetResponse from(Pet pet) {
        PetResponse res = new PetResponse();
        res.setId(pet.getId());
        res.setName(pet.getName());
        res.setPetType(pet.getPetType());
        res.setPetTypeLabel(pet.getPetType().getDescription());
        res.setBreed(pet.getBreed());
        res.setWeight(pet.getWeight());
        res.setAge(pet.getAge());
        res.setOwnerName(pet.getOwner().getName());
        res.setSizeCategory(pet.getSizeCategory());
        res.setSizeCategoryLabel(pet.getSizeCategory().getLabel());
        res.setCoatType(pet.getCoatType());
        res.setCoatTypeLabel(pet.getCoatType().getLabel());
        res.setHasSeparationAnxiety(pet.getHasSeparationAnxiety());
        res.setOwnerPhone(pet.getOwnerPhone());
        res.setNotes(pet.getNotes());
        res.setPhotoUrl(pet.getPhotoUrl());

        // 自動推薦項目：依體型帶入洗澡+吹毛 itemCode
        List<String> recommended = new ArrayList<>();
        PetSizeCategory size = pet.getSizeCategory();
        if (size.getBathItemCode() != null) recommended.add(size.getBathItemCode());
        if (size.getBlowItemCode() != null) recommended.add(size.getBlowItemCode());
        res.setRecommendedItemCodes(recommended);

        return res;
    }
}
