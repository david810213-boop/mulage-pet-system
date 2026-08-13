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

    // ── 需求 19：定型化契約要求蒐集的寵物資料（皆選填）─────────────────
    @Column(length = 10)
    private String gender;              // 性別："公" / "母"

    @Builder.Default
    private Boolean isNeutered = false; // 是否絕育

    @Builder.Default
    @Column(name = "has_chip", nullable = false, columnDefinition = "boolean default false")
    private boolean hasChip = false;    // 是否植入晶片

    @Column(name = "chip_number", length = 50)
    private String chipNumber;          // 晶片號碼（hasChip=true 才有意義；貓咪未辦登記可留空）

    @Column(name = "personality_tags", length = 200)
    private String personalityTags;     // 個性，逗號分隔（例如「親近人,容易緊張」）

    @Column(name = "health_history", length = 300)
    private String healthHistory;       // 病史，逗號分隔

    @Column(name = "health_history_other", length = 200)
    private String healthHistoryOther;  // 病史「其他」欄位對應的文字

    @Builder.Default
    @Column(name = "has_designated_vet", nullable = false, columnDefinition = "boolean default false")
    private boolean hasDesignatedVet = false; // 是否有指定獸醫院

    @Column(name = "designated_vet_name", length = 100)
    private String designatedVetName;

    @Column(name = "designated_vet_address", length = 200)
    private String designatedVetAddress;

    @Column(name = "designated_vet_phone", length = 20)
    private String designatedVetPhone;

    // ── 關聯 ──────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User owner;
}
