package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.TopUpStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 線上儲值申請（僅限轉帳）
 *
 * 需求 1：線上儲值僅限轉帳。
 * 顧客在 LIFF 填寫「金額 + 匯款帳號後五碼 + 轉帳時間」送出，
 * 這時只建立一筆 PENDING 申請，錢包餘額「不會」立刻變動。
 * 店家於後台核對銀行入帳後按「確認入帳」，才真正呼叫 WalletService.deposit() 加值。
 */
@Entity
@Table(name = "topup_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 申請的顧客
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    // 申請儲值金額
    @Column(nullable = false)
    private Integer amount;

    // 匯款帳號後五碼（店家核帳比對用）
    @Column(name = "last_five_digits", length = 10)
    private String lastFiveDigits;

    // 顧客填寫的轉帳時間 / 備註（例如：01/15 14:30 轉帳）
    @Column(length = 200)
    private String remitNote;

    // 狀態：預設 PENDING（待核帳）
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TopUpStatus status = TopUpStatus.PENDING;

    // 建立時間（顧客送出）
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 核帳時間（店家確認 / 駁回）
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // 由哪位店家 / 員工核帳（顯示用字串）
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    // 駁回原因（選填）
    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
