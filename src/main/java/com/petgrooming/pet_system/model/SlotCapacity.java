package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 需求 3：同時段最多五隻。
 *
 * 這張表是「時段計數器」。每個 (日期, 開始時間) 一列，記錄目前已預約幾隻(booked)、上限(capacity=5)。
 *
 * 為什麼需要獨立計數表？
 *   併發下，「先 count 再 insert」會有 phantom read：五個人同時 count 到 4，全部通過檢查 → 超賣。
 *   在既有 appointments 上做悲觀鎖無法鎖「還不存在的列」。
 *   因此改為對「這一列計數器」加悲觀鎖(SELECT ... FOR UPDATE)，同時段的預約請求會在鎖上排隊，
 *   逐一讀到最新 booked 值，杜絕超賣。
 */
@Entity
@Table(
    name = "slot_capacity",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_slot_date_time",
        columnNames = {"slot_date", "slot_time"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;      // 時段開始時間（11:00 / 13:00 / 15:00 / 17:00）

    @Column(nullable = false)
    @Builder.Default
    private int booked = 0;          // 目前已預約數

    @Column(nullable = false)
    @Builder.Default
    private int capacity = 5;        // 上限（需求：最多 5 隻）
}
