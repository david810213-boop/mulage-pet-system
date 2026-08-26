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
    private Double age; // 需求（追加）：允許小數（例如 5.5 歲）
    private String ownerName;

    // 新增欄位
    private PetSizeCategory sizeCategory;       // 體型（系統自動判斷）
    private String sizeCategoryLabel;           // 體型中文顯示
    private CoatType coatType;                  // 毛長
    private String coatTypeLabel;                // 毛長中文
    // 需求（追加）：菜單簡化——貓咪毛髮分類（單層毛/雙層毛/長毛），依品種自動判斷，
    // LIFF 預約頁靠這個欄位篩選對應的套餐選單。狗/特殊貓種是 null。
    private com.petgrooming.pet_system.enums.CatCoatCategory catCoatCategory;
    private String catCoatCategoryLabel;
    private Boolean hasSeparationAnxiety;       // 分離焦慮
    private String ownerPhone;                  // 家長手機
    private String notes;                       // 注意事項
    private String photoUrl;                    // 需求 17：寵物照片（大頭照）

    // 系統根據體型自動推薦的服務 itemCode（預約時直接帶入）
    private List<String> recommendedItemCodes;

    // 需求（追加）：這隻寵物是不是既有客戶（有沒有任何一筆已結帳消費紀錄），
    // 供預約表單 JS 判斷「僅限既有客戶」的項目要不要顯示在選單裡。
    private Boolean isExistingCustomer;

    // 需求（追加，2026-08-24）：狗狗定價流程簡化——鎖定的固定套餐。
    // lockedGroomingItemId 為 null 代表還沒鎖定（LIFF/店員開單依體重自動篩選）；
    // 有值的話，lockedItemName/lockedItemPrice 是為了讓前端不用再多打一次 API
    // 查這個項目的名稱/價格，直接從這裡拿（PetService 組裝時一併查好塞進來）。
    private Long lockedGroomingItemId;
    private String lockedItemName;
    private Double lockedItemPrice;

    // 需求（追加，2026-08-24 修正）：需求19定型化契約蒐集的寵物資料，原本這個
    // DTO 完全沒有暴露這幾個欄位，導致 LIFF 編輯毛孩再次打開編輯畫面時，這些
    // 欄位永遠看起來是空的（不是前端沒填，是後端 API 本來就沒有把資料吐出來）。
    private String gender;
    private Boolean isNeutered;
    private boolean hasChip;
    private String chipNumber;
    private String personalityTags;      // 逗號分隔字串，例如「親近人,容易緊張」
    private String healthHistory;        // 逗號分隔字串
    private String healthHistoryOther;
    private boolean hasDesignatedVet;
    private String designatedVetName;
    private String designatedVetAddress;
    private String designatedVetPhone;

    public static PetResponse from(Pet pet) {
        return from(pet, null);
    }

    /** @param lockedItem 這隻寵物鎖定的固定套餐項目，還沒鎖定或呼叫端不需要顯示名稱/價格時傳 null。 */
    public static PetResponse from(Pet pet, com.petgrooming.pet_system.model.GroomingItem lockedItem) {
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
        res.setCatCoatCategory(pet.getCatCoatCategory());
        res.setCatCoatCategoryLabel(pet.getCatCoatCategory() != null ? pet.getCatCoatCategory().getLabel() : null);
        res.setHasSeparationAnxiety(pet.getHasSeparationAnxiety());
        res.setOwnerPhone(pet.getOwnerPhone());
        res.setNotes(pet.getNotes());
        res.setPhotoUrl(pet.getPhotoUrl());
        res.setLockedGroomingItemId(pet.getLockedGroomingItemId());
        if (lockedItem != null) {
            res.setLockedItemName(lockedItem.getName());
            res.setLockedItemPrice(lockedItem.getPrice());
        }
        // 需求（追加，2026-08-24 修正）：補上定型化契約蒐集的欄位
        res.setGender(pet.getGender());
        res.setIsNeutered(pet.getIsNeutered());
        res.setHasChip(pet.isHasChip());
        res.setChipNumber(pet.getChipNumber());
        res.setPersonalityTags(pet.getPersonalityTags());
        res.setHealthHistory(pet.getHealthHistory());
        res.setHealthHistoryOther(pet.getHealthHistoryOther());
        res.setHasDesignatedVet(pet.isHasDesignatedVet());
        res.setDesignatedVetName(pet.getDesignatedVetName());
        res.setDesignatedVetAddress(pet.getDesignatedVetAddress());
        res.setDesignatedVetPhone(pet.getDesignatedVetPhone());

        // 自動推薦項目：依體型帶入洗澡+吹毛 itemCode
        List<String> recommended = new ArrayList<>();
        PetSizeCategory size = pet.getSizeCategory();
        if (size.getBathItemCode() != null) recommended.add(size.getBathItemCode());
        if (size.getBlowItemCode() != null) recommended.add(size.getBlowItemCode());
        res.setRecommendedItemCodes(recommended);

        return res;
    }
}
