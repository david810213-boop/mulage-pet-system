package com.petgrooming.pet_system.service.discount;

import com.petgrooming.pet_system.enums.DiscountType;

/** 單一項目擇優後的價格與實際套用的折扣種類。 */
public record DiscountResolution(double price, DiscountType type) {
}
