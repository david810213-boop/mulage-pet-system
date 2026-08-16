package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 需求 6：店用洗劑每次領用的結構化紀錄，供財務報表計算「洗劑消耗成本」用。
 *
 * 之前 StoreSupplyService.recordUsage() 只把領用寫進操作紀錄（純文字備註，
 * 沒辦法拿來做統計查詢/加總）。這裡另外存一份結構化紀錄，成本用「領用當下」
 * 的進貨單價快照（unitCostSnapshot），不是事後回頭抓 StoreSupply 目前的單價——
 * 避免之後進貨單價調整了，回頭去算以前月份的成本卻用了錯的價格。
 */
@Entity
@Table(name = "supply_usage_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplyUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supply_id", nullable = false)
    private Long supplyId;

    @Column(name = "supply_name", nullable = false, length = 100)
    private String supplyName; // 快照，之後品項改名/下架不影響歷史紀錄可讀性

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost_snapshot", nullable = false)
    private int unitCostSnapshot; // 領用當下的進貨單價，不是事後回頭查

    @Column(name = "used_by_username", nullable = false, length = 100)
    private String usedByUsername;

    @Column(name = "used_by_name", length = 100)
    private String usedByName;

    @Column(length = 200)
    private String note;

    @Column(name = "used_at", nullable = false)
    @Builder.Default
    private LocalDateTime usedAt = LocalDateTime.now();
}
