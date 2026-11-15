package com.petgrooming.pet_system.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 員工核帳頁：一筆待核帳的訂金匯款。outstandingNow 是核帳當下這一天仍未付清的金額。 */
public record DepositPaymentView(
        Long id,
        Long userId,
        String customerName,
        LocalDate date,
        int amount,
        String lastFiveDigits,
        String remitNote,
        LocalDateTime createdAt,
        int outstandingNow) {
}
