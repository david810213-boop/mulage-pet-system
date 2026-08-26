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
    private Double age;                // 年齡（需求追加：允許小數，例如 5.5 歲，方便幼犬/幼貓標記半歲）

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

    // 需求（追加）：菜單簡化——貓咪專用，新增/編輯毛孩時依品種（下拉選單）自動查
    // CatBreedCoatMapping 對照表算出來，寫進這裡。null 代表這隻是狗、或是品種不在
    // 對照表裡的特殊貓種（LIFF 預約頁遇到 null 會顯示全部貓咪套餐項目，不會篩選，
    // 讓店家人工協助判斷，不會擋住顧客預約）。
    // 跟 sizeCategory/coatType 一樣是系統自動判斷欄位，顧客不會直接填這個值，
    // 只能透過選品種間接觸發。
    @Enumerated(EnumType.STRING)
    @Column(name = "cat_coat_category")
    private com.petgrooming.pet_system.enums.CatCoatCategory catCoatCategory;

    // 需求（追加，2026-08-24）：狗狗定價流程簡化——成犬定型後，店員在店裡核對
    // 選出真正對應的套餐項目，這裡記錄下來變成這隻狗的「固定套餐」。
    // null（預設）＝還沒鎖定，LIFF 預約/店員開單都依 Pet.weight 目前的體重
    // 自動篩選對應級距的 6 個項目，每次都要重新選；有值＝已鎖定，之後不管顧客
    // 自己在 LIFF 訂、還是店員開單，都直接帶出這個固定項目跟價格，不再顯示選單。
    // 只對狗狗有意義（貓咪走的是 90 天回洗優惠 + 首次體驗優惠那一套邏輯，
    // 沒有「鎖定固定套餐」這個概念），但欄位本身沒有限制只能狗狗使用，
    // 純粹是因為目前只有狗狗的業務流程會用到。
    // 店員如果需要重新報價（狗狗生病消瘦、換季毛況差很多、當初選錯了），
    // 把這個欄位清空（設回 null）就會恢復成「依體重自動篩選」的狀態。
    @Column(name = "locked_grooming_item_id")
    private Long lockedGroomingItemId;

    // 需求（追加，2026-08-26 修正）：刪除寵物改成跟服務項目下架同一套邏輯——
    // 軟刪除，不是真的從資料庫刪掉這筆資料列。這樣過往預約/消費紀錄裡
    // 記錄的寵物名字快照完全不受影響（本來就不是即時關聯查詢，是結帳當下
    // 存好的文字），刪除也不用再檢查「有沒有消費紀錄」這件事——反正資料
    // 沒有真的消失，只是不再出現在顧客/店員的主動選擇清單裡而已。
    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isDeleted = false;

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

    // ── 需求 17：寵物照片（Cloudinary 雲端圖床，存網址不存檔案本體）──────
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "photo_public_id", length = 200)
    private String photoPublicId; // Cloudinary 的 public_id，換照片時用來刪除舊圖

    // ── 關聯 ──────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User owner;
}
