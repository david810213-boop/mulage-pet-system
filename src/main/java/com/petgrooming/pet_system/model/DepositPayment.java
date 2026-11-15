package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.DepositPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 顧客回報的訂金匯款（目前人工核帳）。
 *
 * 訂金以「同一會員同一天」為單位收取：顧客填寫匯款帳號後五碼送出時，記下當下該日尚未付清的金額；
 * 店家核對銀行入帳後確認，金額分配到當天各筆預約，付清後待付訂金的預約自動轉為已確認。
 */
@Entity
@Table(name = "deposit_payments", indexes = {
        @Index(name = "idx_deposit_payments_status_created", columnList = "status, created_at"),
        @Index(name = "idx_deposit_payments_user_date", columnList = "user_id, appointment_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    // 這筆訂金對應的預約日期
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "last_five_digits", length = 10)
    private String lastFiveDigits;

    @Column(name = "remit_note", length = 200)
    private String remitNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'PENDING'")
    @Builder.Default
    private DepositPaymentStatus status = DepositPaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;
}
