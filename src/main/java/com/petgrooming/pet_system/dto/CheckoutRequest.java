package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.PaymentMethod;
import lombok.Data;

// 結帳請求
@Data
public class CheckoutRequest {
    private PaymentMethod paymentMethod;    // CASH / CREDIT_CARD / LINE_PAY
}