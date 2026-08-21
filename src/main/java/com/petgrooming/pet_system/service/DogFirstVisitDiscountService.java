package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * 需求（追加）：狗狗首次體驗 9 折。
 *
 * 規則：這隻狗名下完全沒有任何一筆「已結帳」的預約或現場開單紀錄
 * （不管有沒有洗澡項目，只要曾經消費過一次就不算首次），這次消費的
 * 狗狗基礎洗吹package 項目自動打 9 折。
 *
 * 跟貓咪 90 天回洗優惠是兩套獨立機制：
 *   - 觸發條件相反：回洗優惠看「有沒有回訪」，首次體驗看「是不是第一次」
 *   - 適用品項範圍：只套用在狗狗洗吹 package 項目（PerformanceCategory
 *     為 BATH_SMALL / BATH_LARGE，對應「小型犬洗吹」「大型犬洗吹」），
 *     不套用在耳道調理、接送、除廢毛等額外加購項目——跟貓咪回洗優惠
 *     只套用在洗澡項目、不套用在 AD/HC 等加購項目，是同樣的設計原則。
 *   - 一樣跟會員儲值折扣「只能擇一」，取對顧客較優惠的那個，不疊加。
 *
 * 資料來源涵蓋範圍：跟 CatRewashDiscountService 一致——預約（Appointment）
 * 與現場開單（WalkInOrder，須有綁定會員）都納入判斷是否為首次消費。
 *
 * 需求（追加）：「是不是既有客戶」的查詢邏輯已抽成 {@link PetConsumptionHistoryService}
 * 共用，這裡不再自己重複實作，改成呼叫共用 service。
 */
@Service
@RequiredArgsConstructor
public class DogFirstVisitDiscountService {

    public static final double FIRST_VISIT_DISCOUNT_RATE = 0.9; // 9 折

    private static final Set<PerformanceCategory> DOG_PACKAGE_CATEGORIES =
            EnumSet.of(PerformanceCategory.BATH_SMALL, PerformanceCategory.BATH_LARGE);

    private final PetRepository petRepository;
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 需求（追加）

    public boolean isDogPackageItem(GroomingItem item) {
        return item != null && DOG_PACKAGE_CATEGORIES.contains(item.getPerformanceCategory());
    }

    public boolean isDogPackageCategory(PerformanceCategory category) {
        return DOG_PACKAGE_CATEGORIES.contains(category);
    }

    /** 這筆預約結帳時，這隻狗是否符合「首次消費」資格。只有狗（petType == DOG）才可能符合。 */
    public boolean isFirstVisitEligible(Appointment appointment) {
        if (!"DOG".equalsIgnoreCase(appointment.getPetType())) return false;
        return !petConsumptionHistoryService.hasPriorPaidService(
                appointment.getUser().getId(), appointment.getPetName(), appointment.getId());
    }

    /** 現場開單（有綁定會員）結帳時，是否符合首次消費資格；沒綁會員無法識別歷史，一律不適用。 */
    public boolean isFirstVisitEligible(WalkInOrder order) {
        if (order.getMember() == null) return false;
        boolean isDog = petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .map(pet -> pet.getPetType() == PetType.DOG)
                .orElse(false);
        if (!isDog) return false;
        return !petConsumptionHistoryService.hasPriorPaidService(
                order.getMember().getId(), order.getPetName(), order.getId());
    }

    /**
     * 首次體驗優惠與會員儲值折扣「只能擇一」，取對顧客較優惠的那個；跟
     * CatRewashDiscountService.resolvePreferredDiscount 完全同樣的邏輯，
     * 只是折扣種類換成 FIRST_VISIT。
     */
    public DiscountResolution resolvePreferredDiscount(double price,
                                                        boolean firstVisitApplicable,
                                                        boolean memberDiscountApplicable,
                                                        double memberDiscountRate) {
        if (firstVisitApplicable && memberDiscountApplicable) {
            double firstVisitPrice = price * FIRST_VISIT_DISCOUNT_RATE;
            double memberPrice = price * memberDiscountRate;
            return firstVisitPrice <= memberPrice
                    ? new DiscountResolution(firstVisitPrice, DiscountType.FIRST_VISIT)
                    : new DiscountResolution(memberPrice, DiscountType.MEMBER);
        }
        if (firstVisitApplicable) {
            return new DiscountResolution(price * FIRST_VISIT_DISCOUNT_RATE, DiscountType.FIRST_VISIT);
        }
        if (memberDiscountApplicable) {
            return new DiscountResolution(price * memberDiscountRate, DiscountType.MEMBER);
        }
        return new DiscountResolution(price, DiscountType.NONE);
    }

    public record DiscountResolution(double price, DiscountType type) {}
}

