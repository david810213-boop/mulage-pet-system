package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.model.Transaction;
import lombok.Data;

import java.time.LocalDateTime;

// API 回傳的交易資料（對應原本 Transaction.toString()）
@Data
public class TransactionResponse {
    private Long id;
    private Long appointmentId;         // 需求：交易紀錄列表點擊查看明細用
    private String appointmentCode;     // AP001 格式
    private String ownerEmail;
    private PaymentMethod paymentMethod;
    private int baseAmount;             // 原始金額
    private int finalAmount;            // 實付金額
    private boolean paid;
    private LocalDateTime paymentTime;
    private String handledBy;

    public static TransactionResponse from(Transaction t) {
        TransactionResponse res = new TransactionResponse();
        res.setId(t.getId());
        res.setAppointmentId(t.getAppointment().getId());
        res.setAppointmentCode(String.format("AP%03d", t.getAppointment().getId()));
        res.setOwnerEmail(t.getUser().getUsername());
        res.setPaymentMethod(t.getPaymentMethod());
        res.setBaseAmount(t.getBaseAmount());
        res.setFinalAmount(t.getFinalAmount());
        res.setPaid(t.isPaid());
        res.setPaymentTime(t.getPaymentTime());
        res.setHandledBy(t.getHandledBy());
        return res;
    }
}