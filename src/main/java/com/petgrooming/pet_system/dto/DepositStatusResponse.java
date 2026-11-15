package com.petgrooming.pet_system.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 顧客某一天的訂金狀態（LIFF 我的預約頁顯示匯款資訊用）。
 *
 * @param required       當天應付訂金合計
 * @param paid           已入帳合計
 * @param outstanding    尚未付清的金額
 * @param dueAt          付款期限（最早到期的待付訂金預約）
 * @param paymentPending 已回報匯款、店家核帳中
 * @param pendingAmount  核帳中的金額
 * @param lastRejectReason 最近一次被駁回的原因（沒有則為 null）
 */
public record DepositStatusResponse(
        LocalDate date,
        int required,
        int paid,
        int outstanding,
        LocalDateTime dueAt,
        boolean paymentPending,
        int pendingAmount,
        String lastRejectReason,
        String bankName,
        String accountNumber,
        String accountHolder,
        String qrCodeUrl) {
}
