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