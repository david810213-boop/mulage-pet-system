package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.petgrooming.pet_system.enums.PaymentMethod;
import lombok.Data;

// 結帳請求
@Data
public class CheckoutRequest {
    @Schema(description = "CASH / CREDIT_CARD / LINE_PAY")
    private PaymentMethod paymentMethod;
}