package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 需求（追加）：套餐化——一個服務項目（套餐）結帳時，除了本身的「主組成」
 * （已經記錄在 {@link GroomingItem} 的 performanceCategory/points 欄位），
 * 還可能同時包含其他積分分類，例如「頂級專業定制洗護」= 洗澡（主組成，掛價格）
 * + 吹毛 + 基礎美容 + AD 三個「副組成」。
 *
 * 副組成不重複計價（price 固定 0，錢已經在主組成算過了），純粹是為了讓待補經手人
 * 矩陣表單能正確拆出「這次消費做了哪些積分項目」，可能由不同美容師分開處理。
 */
@Entity
@Table(name = "grooming_item_components")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroomingItemComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grooming_item_id", nullable = false)
    private GroomingItem groomingItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_category", nullable = false)
    private PerformanceCategory performanceCategory;

    @Column(name = "points", nullable = false)
    private Double points;
}
