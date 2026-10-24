package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import com.petgrooming.pet_system.service.discount.SpecialDiscountPolicy;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * 貓咪首次體驗 9 折：系統自動判斷這隻貓是不是第一次消費，是的話自動打 9 折，
 * 不用讓客人或店員自己判斷（比照狗狗首次體驗優惠）。
 *
 * 規則：這隻貓名下完全沒有任何一筆「已結帳」的預約或現場開單紀錄（不管有沒有
 * 洗澡項目，只要曾經消費過一次就不算首次），這次消費的貓咪洗澡套餐項目
 * （CAT013~024，performanceCategory 為 BATH_CAT_S / BATH_CAT_L）自動打 9 折。
 *
 * 跟貓咪 90 天回洗優惠是天生互斥、不會同時符合的兩套判斷：回洗優惠的前提是
 * 「找得到上一次洗澡日期」，首次體驗的前提是「完全沒有任何消費紀錄」——
 * 一隻貓不可能同時符合這兩個條件（優先順序見 DiscountCalculator）。
 *
 * 跟會員儲值折扣一樣「只能擇一」，取對顧客較優惠的那個，不疊加——這部分邏輯
 * 跟 DogFirstVisitDiscountService 完全一致，擇優規則統一由 DiscountCalculator 計算。
 */
@Service
@RequiredArgsConstructor
public class CatFirstVisitDiscountService implements SpecialDiscountPolicy {

    public static final double FIRST_VISIT_DISCOUNT_RATE = 0.9; // 9 折

    private static final Set<PerformanceCategory> CAT_BATH_CATEGORIES =
            EnumSet.of(PerformanceCategory.BATH_CAT_S, PerformanceCategory.BATH_CAT_L);

    private final PetRepository petRepository;
    private final PetConsumptionHistoryService petConsumptionHistoryService;

    public boolean isCatBathItem(GroomingItem item) {
        return item != null && CAT_BATH_CATEGORIES.contains(item.getPerformanceCategory());
    }

    public boolean isCatBathCategory(PerformanceCategory category) {
        return CAT_BATH_CATEGORIES.contains(category);
    }

    /** 這筆預約結帳時，這隻貓是否符合「首次消費」資格。只有貓（petType == CAT）才可能符合。 */
    public boolean isFirstVisitEligible(Appointment appointment) {
        if (!"CAT".equalsIgnoreCase(appointment.getPetType())) return false;
        return !petConsumptionHistoryService.hasPriorPaidService(
                appointment.getUser().getId(), appointment.getPetName(), appointment.getId());
    }

    /** 現場開單（有綁定會員）結帳時，是否符合首次消費資格；沒綁會員無法識別歷史，一律不適用。 */
    public boolean isFirstVisitEligible(WalkInOrder order) {
        if (order.getMember() == null) return false;
        boolean isCat = petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .map(pet -> pet.getPetType() == PetType.CAT)
                .orElse(false);
        if (!isCat) return false;
        return !petConsumptionHistoryService.hasPriorPaidService(
                order.getMember().getId(), order.getPetName(), order.getId());
    }

    // ── SpecialDiscountPolicy：貓咪首次體驗優惠（擇優規則見 DiscountCalculator）──

    @Override
    public DiscountType type() {
        return DiscountType.FIRST_VISIT;
    }

    @Override
    public double rate() {
        return FIRST_VISIT_DISCOUNT_RATE;
    }

    @Override
    public boolean appliesTo(PerformanceCategory category) {
        return isCatBathCategory(category);
    }

    @Override
    public boolean isEligible(Appointment appointment) {
        return isFirstVisitEligible(appointment);
    }

    @Override
    public boolean isEligible(WalkInOrder order) {
        return isFirstVisitEligible(order);
    }
}
