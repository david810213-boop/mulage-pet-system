package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.MemberCardTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 顧客錢包
 * - 一個顧客對應一個錢包（One-to-One with User）
 * - 記錄餘額、會員卡等級、開卡日期
 * - 會員名下所有寵物共用同一個錢包
 */
@Entity
@Table(name = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 一個顧客只有一個錢包
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    private User user;

    // 目前餘額（單位：元，不使用浮點數避免精度問題）
    @Column(nullable = false)
    @Builder.Default
    private Integer balance = 0;

    // 會員卡等級（依歷史最高單筆儲值金額決定）
    @Enumerated(EnumType.STRING)
    @Column(name = "card_tier", nullable = false)
    @Builder.Default
    private MemberCardTier cardTier = MemberCardTier.NONE;

    // 開卡日期（第一次達到 VILLAGE 以上等級時記錄）
    @Column(name = "card_activated_at")
    private LocalDate cardActivatedAt;

    // 會員資格到期日（開卡後 365 天）
    @Column(name = "card_expires_at")
    private LocalDate cardExpiresAt;

    // 最後異動時間
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 判斷會員資格是否仍在有效期內
     */
    public boolean isCardActive() {
        if (cardTier == MemberCardTier.NONE) return false;
        // 村民優惠方案沒有折扣也沒有期限（2026-10-02 店家確認），一律視為有效
        if (cardTier == MemberCardTier.VILLAGE) return true;
        if (cardExpiresAt == null) return false;
        return !LocalDate.now().isAfter(cardExpiresAt);
    }

    /**
     * 取得目前有效的折扣率
     * 若會員資格已過期，折扣無效（回傳 1.0）
     */
    public double getEffectiveDiscount() {
        if (!isCardActive()) return 1.0;
        return cardTier.getDiscount();
    }
}
