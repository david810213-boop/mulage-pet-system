package com.petgrooming.pet_system.dto;

import java.time.LocalDate;

/**
 * 待補經手人頁「補第二位」區塊的一列：已有第一位經手人、還沒有第二位的項目。
 *
 * @param kind A＝預約、W＝現場單
 */
public record SecondOperatorCandidateView(
        String kind,
        Long itemId,
        Long orderId,
        String code,
        String petName,
        LocalDate date,
        String categoryLabel,
        String itemName,
        double points,
        Long firstOperatorId,
        String firstOperatorName) {
}
