package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * 需求（追加，2026-08-27）：預設時段容量範本。
 *
 * 背景：店家反饋預約時間過於鬆散，希望能針對「一天當中的某個時段」設定固定的預設可接組數
 * （例如每天 11:00 固定可接 4 組、11:30 這格預設不開放、12:00 起每 30 分鐘固定接 1 組），
 * 不用每天都重新手動輸入一次。
 *
 * 這張表只存「時段（時間點）→ 預設名額上限」，通用一份，不分星期幾、不分日期。
 * 跟既有的 SlotCapacity（每一天每個時段實際的計數列）是兩層不同的機制：
 *   - DefaultSlotCapacityTemplate：範本，決定「這個時段預設可以接幾組」
 *   - SlotCapacity：某一天某個時段實際的即時狀態（已預約數 + 目前上限），
 *     目前上限初始值改為讀這份範本，之後店家仍可以在「時段管理」頁面針對某一天做覆寫調整
 *     （例如某天有客人一次帶五隻寵物，臨時關閉後面幾個時段），覆寫只影響那一天，不影響範本本身。
 */
@Entity
@Table(
    name = "default_slot_capacity_template",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_template_slot_time",
        columnNames = {"slot_time"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefaultSlotCapacityTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;      // 時段開始時間（例如 11:00 / 11:30 / 12:00 ...）

    @Column(nullable = false)
    @Builder.Default
    private int capacity = 5;        // 這個時段預設的名額上限，設 0 代表這個時段預設不開放
}
