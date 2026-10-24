package com.petgrooming.pet_system.service.discount;

import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.WalkInOrder;

/**
 * 服務促銷（首次體驗優惠、貓咪 90 天回洗優惠等）。
 *
 * 每種促銷由兩層條件決定：這筆單（這隻寵物這次消費）有沒有資格，以及哪些項目適用（依積分分類）。
 * 促銷不限付款方式；跟會員儲值折扣只能擇一，由 {@link DiscountCalculator} 逐項目取對顧客較優惠者。
 * 新增一種促銷：實作這個介面，並加進 DiscountCalculator 的優先順序清單。
 */
public interface SpecialDiscountPolicy {

    DiscountType type();

    /** 折扣率，例如 0.9 代表 9 折。 */
    double rate();

    /** 這個積分分類的項目是否適用這個促銷。 */
    boolean appliesTo(PerformanceCategory category);

    /** 這筆預約結帳時是否有資格。 */
    boolean isEligible(Appointment appointment);

    /** 這張現場單結帳時是否有資格（沒綁會員無法識別歷史紀錄，一律不適用）。 */
    boolean isEligible(WalkInOrder order);
}
