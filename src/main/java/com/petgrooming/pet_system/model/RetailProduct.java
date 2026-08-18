package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 需求 7-1：零售商品（店家自訂的可販售商品，例如零食、玩具、清潔用品）。
 * 跟美容服務項目（GroomingItem）是兩套獨立體系：
 *   - GroomingItem：計積分的美容服務，員工績效依此結算
 *   - RetailProduct：純粹販售的商品，不計積分，結帳時直接扣庫存
 */
@Entity
@Table(name = "retail_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetailProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int price;

    // 需求 6：進貨成本單價（供財務報表計算毛利用；跟 price 賣價是不同概念）
    // 原本需求 7-1 沒有要求記錄成本，這裡是財務報表需要才補上的欄位，預設 0
    // （代表還沒設定成本，財務報表上會顯示成本為 0，不影響其他既有功能運作）。
    @Column(name = "unit_cost", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private int unitCost = 0;

    // 目前庫存量（結帳扣庫存、後台可手動調整）
    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private int stockQuantity = 0;

    @Column(length = 300)
    private String description;

    // 軟刪除：下架的商品不再顯示於加購清單，但保留歷史訂單的關聯資料
    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
