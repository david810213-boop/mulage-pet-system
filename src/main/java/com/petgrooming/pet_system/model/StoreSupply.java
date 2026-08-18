package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 需求 7-2：店用洗劑（店家自己使用的消耗品，例如洗毛精、潤絲精）。
 * 跟零售商品（RetailProduct）的關鍵差異：
 *   - 不賣給顧客，員工「領用」才扣庫存（不是結帳扣庫存）
 *   - 要記錄進貨成本（算進店家支出，供財務報表使用）
 *   - 低於安全庫存量會自動發 LINE 通知全體人員叫貨
 */
@Entity
@Table(name = "store_supplies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private int stockQuantity = 0;

    // 安全庫存量：低於這個數字就會觸發 LINE 通知
    @Column(name = "safety_stock_threshold", nullable = false)
    @Builder.Default
    private int safetyStockThreshold = 0;

    // 進貨單價成本（供財務報表計算成本用，不是賣價，因為這東西根本不賣）
    @Column(name = "unit_cost", nullable = false)
    @Builder.Default
    private int unitCost = 0;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
