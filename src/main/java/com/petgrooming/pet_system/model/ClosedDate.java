package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求 16：公休日設定。
 *
 * 與需求 1（SlotCapacity 單一時段開關）是同一套底層概念的延伸，但獨立成一張表：
 *   - 需求 1：調整「特定日期＋特定時段」的名額上限（細粒度，逐時段）
 *   - 需求 16：整天標記公休（粗粒度，一次關閉當天所有時段）
 * 兩者互不覆蓋、可並存——公休日設定生效時，getAvailableSlots() 直接回傳空清單，
 * 不需要也不會去改動 SlotCapacity 底下各時段原本的 capacity 數字。
 */
@Entity
@Table(
    name = "closed_date",
    uniqueConstraints = @UniqueConstraint(name = "uk_closed_date", columnNames = "closed_date")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosedDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closed_date", nullable = false)
    private LocalDate date;

    @Column(length = 100)
    private String reason;   // 選填，例如「農曆春節」「店休」，僅供後台顯示辨識用

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
