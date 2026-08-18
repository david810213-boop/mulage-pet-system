package com.petgrooming.pet_system.enums;

public enum PaymentMethod {
    CASH("現金",       0.00),
    CREDIT_CARD("信用卡", 0.02),   // 2% 手續費（需求 10：結帳畫面已不再提供此選項，僅保留舊資料相容用）
    LINE_PAY("LinePay",  0.00),
    WALLET("儲值金",     0.00),  // 使用會員儲值金餘額扣款，無手續費
    WIRE_TRANSFER("匯款", 0.00); // 需求 10：需經店家人工「確認收款」後訂單才轉為已完成

    private final String displayName;
    private final double feeRate;   // 正數 = 加收手續費，負數 = 打折

    PaymentMethod(String displayName, double feeRate) {
        this.displayName = displayName;
        this.feeRate = feeRate;
    }

    public String getDisplayName() { return displayName; }

    // 計算最終金額（對應原本各 PaymentSystem.calculateTotal）
    public int calculateFinalAmount(int baseAmount) {
        return (int) (baseAmount * (1 + feeRate));
    }
}