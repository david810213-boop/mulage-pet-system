package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每月績效結算
 * 月底自動統整，計算總積分與對應獎勵金
 */
@Entity
@Table(name = "monthly_performance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"staff_id", "perf_month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    @ToString.Exclude
    private User staff;

    // 統計月份（格式：2026-06-01，取當月第一天代表整月）
    // 欄位名不能叫 year_month：這是 MySQL 的保留字（INTERVAL ... YEAR_MONTH 語法用），
    // 不加反引號跳脫直接當一般欄位名會導致 CREATE TABLE 語法錯誤。改名為 perf_month 避開。
    @Column(name = "perf_month", nullable = false)
    private LocalDate yearMonth;

    // 當月總積分（不含接待）
    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Double totalPoints = 0.0;

    // 接待積分（單獨統計）
    @Column(name = "reception_points", nullable = false)
    @Builder.Default
    private Double receptionPoints = 0.0;

    // 對應的獎勵金（依積分級距查表）
    @Column(name = "bonus_amount", nullable = false)
    @Builder.Default
    private Integer bonusAmount = 0;

    // 是否已結算（結算後不可修改）
    @Column(name = "is_settled", nullable = false)
    @Builder.Default
    private Boolean isSettled = false;

    // 結算時間
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
