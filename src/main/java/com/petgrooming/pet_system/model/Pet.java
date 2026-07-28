package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.enums.PetType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── 基本資料 ──────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String name;                // 毛孩名字

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PetType petType;            // DOG / CAT / OTHER

    @Column(nullable = false)
    private String breed;               // 品種

    @Column(nullable = false)
    private Double weight;              // 體重（kg）

    @Column(nullable = false)
    private Integer age;                // 年齡

    // ── 自動判斷欄位（新增寵物時系統自動計算）────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "size_category", nullable = false)
    @Builder.Default
    private PetSizeCategory sizeCategory = PetSizeCategory.OTHER; // 體型（小型犬/大型犬/小貓/大貓）

    // 需求 2：毛長不再由顧客選填，改由店家於後台檢視實際毛況後定義。
    // 新增寵物時一律預設 UNDEFINED（未定義），待店家後台設定。
    @Enumerated(EnumType.STRING)
    @Column(name = "coat_type", nullable = false)
    @Builder.Default
    private CoatType coatType = CoatType.UNDEFINED;                // 毛長（未定義 → 店家定義後：短/中長/長）

    // ── 預約須知相關欄位 ──────────────────────────────────────────────────
    @Column(name = "has_separation_anxiety", nullable = false)
    @Builder.Default
    private Boolean hasSeparationAnxiety = false;                  // 是否有分離焦慮

    @Column(name = "owner_phone", length = 20)
    private String ownerPhone;                                      // 家長手機

    @Column(length = 500)
    private String notes;                                           // 注意事項

    // ── 關聯 ──────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User owner;
}
