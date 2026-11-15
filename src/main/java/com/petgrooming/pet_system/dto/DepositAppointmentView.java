package com.petgrooming.pet_system.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 員工核帳頁：待付訂金或待退款的預約。 */
public record DepositAppointmentView(
        Long appointmentId,
        String appointmentCode,
        Long userId,
        String customerName,
        String petName,
        LocalDate date,
        LocalTime startTime,
        int share,
        int paid,
        LocalDateTime dueAt,
        int refundAmount,
        int forfeitedAmount,
        String cancelledBy) {
}
