package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * 現場開單的單一項目（需求 5 / 6）。
 *
 * 經手人（operatorStaff）綁定實際員工帳號（User），而非自由輸入文字，
 * 這樣選定後才能直接寫進既有的績效系統（PerformanceRecord），而不是自成一套、
 * 無法跟員工月結算對上的積分。
 *
 * operatorStaff 為 null 代表「未填寫」，開單當下可以先不指定是誰做的，
 * 之後再從待補清單選人補上，補上的瞬間就會建立一筆績效紀錄。
 *
 * 注意：資料庫裡原本還留有一欄舊的 varchar `operator`（早期自由輸入文字版本），
 * 這裡改用新的 `operator_staff_id` 欄位，舊欄位變成沒用到的孤兒欄位，
 * 不影響功能，確認新版運作正常後可以手動下 SQL 刪除：
 *   ALTER TABLE walk_in_order_items DROP COLUMN operator;
 */
@Entity
@Table(name = "walk_in_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalkInOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private WalkInOrder order;

    // 對應的美容項目 id（可為 null，若是完全自訂的臨時項目）
    @Column(name = "grooming_item_id")
    private Long groomingItemId;

    // 項目名稱（快照，避免項目日後改名或下架影響歷史單）
    @Column(name = "item_name", nullable = false)
    private String itemName;

    // 單價（快照）
    @Column(nullable = false)
    private int price;

    // 積分（快照，補填經手人後用於積分結算）
    @Column(nullable = false)
    @Builder.Default
    private double points = 0.0;

    // 績效分類（快照，供積分報表分類）
    @Enumerated(EnumType.STRING)
    @Column(name = "performance_category", length = 30)
    @Builder.Default
    private PerformanceCategory performanceCategory = PerformanceCategory.OTHER;

    // 經手人：綁定實際員工帳號。null = 未填寫。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_staff_id")
    @ToString.Exclude
    private User operatorStaff;

    // 是否已經把這筆的積分寫進 PerformanceRecord 了（避免同一筆項目被重複計入積分）
    @Column(name = "points_awarded", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean pointsAwarded = false;

    public boolean isOperatorFilled() {
        return operatorStaff != null;
    }
}

