package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "grooming_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroomingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Double price;

    // 邏輯刪除
    private boolean isDeleted = false;

    // 需求 4：是否可線上預約。
    // 只有「大美容 / 小美容 / 精緻洗 / 定製洗」設為 true，會出現在 LIFF 預約頁；
    // 其餘調理 / 加購項目為 false，僅供店家現場開單使用。
    //
    // 注意（ddl-auto 陷阱）：這是新增到「既有資料表」的 NOT NULL 欄位。
    // 若只寫 nullable=false 而不給資料庫層級預設值，Hibernate 產生的
    // ALTER TABLE ... ADD COLUMN bookable boolean NOT NULL
    // 會因既有列無值可填而失敗（H2 / MySQL 皆同）。columnDefinition 讓既有列
    // 自動填入 false，新增才能成功。
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean bookable = false;

    // 績效大項分類（決定完成此項目時員工獲得哪個類別的積分）
    @Enumerated(EnumType.STRING)
    @Column(name = "performance_category", nullable = false)
    @Builder.Default
    private PerformanceCategory performanceCategory = PerformanceCategory.OTHER;

    // 積分數（預設從 PerformanceCategory 取，可在後台針對單一項目覆寫）
    @Column(name = "points", nullable = false)
    @Builder.Default
    private Double points = 0.0;
}