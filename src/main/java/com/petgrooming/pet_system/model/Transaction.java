package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
// 索引依實際查詢條件建立（日期、狀態、付款時間、會員等），ddl-auto=update 會自動補建
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_paid_time", columnList = "paid, payment_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 關聯到哪一筆預約
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    @ToString.Exclude
    private Appointment appointment;

    // 關聯到哪個使用者
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;    // CASH / CREDIT_CARD / LINE_PAY

    @Column(nullable = false)
    private int baseAmount;                 // 結帳前原始金額

    @Column(nullable = false)
    private int finalAmount;                // 含手續費 / 折扣後的實付金額

    @Column(nullable = false)
    @Builder.Default
    private boolean paid = false;

    private LocalDateTime paymentTime;      // 付款時間（付款後才填入）

    private String handledBy;              // 經手人（對應原本 staffName）
}