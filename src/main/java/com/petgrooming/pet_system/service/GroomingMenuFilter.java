package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.DogWeightTier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服務項目菜單篩選。
 *
 * 原本寫在 AppointmentMvcController 的兩支 private 方法，員工手機版的
 * 「到店開單」也要用同一套篩選規則；為了避免同一套業務邏輯複製成兩份、
 * 日後改一邊漏改另一邊（claimByPhone 那次的教訓），抽成共用元件，
 * 網頁版跟手機版都呼叫這裡。邏輯內容跟原本完全相同，沒有任何行為變更。
 */
@Component
public class GroomingMenuFilter {

    /**
     * 「僅限既有客戶」＋「適用物種」篩選。
     */
    public List<GroomingItemResponse> filterFor(List<GroomingItemResponse> items,
            boolean isExisting, String petType) {
        return items.stream()
                .filter(i -> isExisting || !i.isRequiresExistingCustomer())
                .filter(i -> i.getApplicablePetType() == null || i.getApplicablePetType().equalsIgnoreCase(petType))
                .toList();
    }

    /**
     * 現場單用：沒綁會員／查不到寵物時物種是 null，這時不篩物種（避免誤擋），
     * 只套「僅限既有客戶」規則；有物種時跟 filterFor() 相同。
     * 跟網頁版現場單核對頁、結帳頁的篩選條件一致。
     */
    public List<GroomingItemResponse> filterForOptionalPetType(List<GroomingItemResponse> items,
            boolean isExisting, String petType) {
        if (petType == null) {
            return items.stream()
                    .filter(i -> isExisting || !i.isRequiresExistingCustomer())
                    .toList();
        }
        return filterFor(items, isExisting, petType);
    }

    /**
     * 在 filterFor() 的基礎上，再依這隻寵物的體型進一步篩選（店家規定，網頁版與手機版一致）：
     * <ul>
     *   <li>狗狗：已鎖定固定套餐只顯示該套餐；否則依體重級距（毛長已定義時再依毛長）篩選。
     *       還沒填體重時不顯示有體重級距的套餐，頁面須以 {@link #needsWeight} 提醒店員先填寫體重</li>
     *   <li>貓咪：只顯示符合毛髮分類的項目；品種不在對照表（沒有分類）時無從判斷，顯示全部</li>
     *   <li>與體型無關的項目（加購、指甲修剪等）一律顯示</li>
     * </ul>
     */
    public List<GroomingItemResponse> filterForPetShape(List<GroomingItemResponse> items,
            boolean isExisting, String petType, PetResponse pet) {
        var base = filterFor(items, isExisting, petType);
        if (pet == null) return base;

        Long lockedItemId = pet.getLockedGroomingItemId();
        boolean isDog = "DOG".equalsIgnoreCase(petType);
        boolean isCat = "CAT".equalsIgnoreCase(petType);

        final boolean weightMissing = needsWeight(petType, pet);
        final DogWeightTier dogTier =
                isDog && lockedItemId == null && !weightMissing
                        ? DogWeightTier.forWeight(pet.getWeight())
                        : null;
        final CoatType petCoatType = pet.getCoatType();
        final boolean coatDefined = isDog
                && (petCoatType == CoatType.SHORT
                        || petCoatType == CoatType.LONG
                        || petCoatType == CoatType.MEDIUM);
        final String catCoatCategory =
                isCat && pet.getCatCoatCategory() != null ? pet.getCatCoatCategory().name() : null;

        return base.stream()
                .filter(i -> {
                    // 狗狗：已鎖定固定套餐的話，只顯示那個固定項目；沒鎖定則依體重級距＋毛長篩選
                    if (i.getDogWeightTier() != null) {
                        if (lockedItemId != null) return i.getId().equals(lockedItemId);
                        if (weightMissing) return false;
                        if (dogTier != null && !i.getDogWeightTier().equals(dogTier.name())) return false;
                        if (dogTier != null && coatDefined && i.getDogCoatLength() != null) {
                            return i.getDogCoatLength().equals(petCoatType.name());
                        }
                        return true;
                    }
                    // 貓咪：依毛髮分類篩選；品種不在對照表裡（分類是 null）時不篩選，顯示全部讓店家人工判斷
                    if (i.getCatCoatCategory() != null) {
                        if (catCoatCategory != null) return i.getCatCoatCategory().equals(catCoatCategory);
                        return true;
                    }
                    // 跟體型/毛髮分類無關的項目（例如指甲修剪、加購項目），不受這層篩選影響
                    return true;
                })
                .toList();
    }

    /** 狗狗沒有鎖定固定套餐、又還沒填體重時，無法判斷適用的套餐，頁面須提醒店員先填寫體重。 */
    public boolean needsWeight(String petType, PetResponse pet) {
        return pet != null
                && "DOG".equalsIgnoreCase(petType)
                && pet.getLockedGroomingItemId() == null
                && (pet.getWeight() == null || pet.getWeight() <= 0);
    }
}
