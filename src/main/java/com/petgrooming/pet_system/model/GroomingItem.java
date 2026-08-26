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
    // 修正：原本只寫 `= false` 沒加 @Builder.Default，Lombok 產生的 builder()
    // 會完全忽略這個初始值（用 boolean 的語言預設值 false 頂著，這次剛好
    // 跟預期值一樣所以沒出過事，但邏輯上是地雷——如果以後改成 `= true`
    // 之類的非語言預設值，builder() 建出來的物件會悄悄跟預期不同，
    // 且編譯器只會給警告不會報錯，很容易沒發現）。加上 @Builder.Default
    // 才會讓 builder() 真的套用這個初始值。
    @Builder.Default
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

    // 需求 5：是否可享會員儲值金折扣。洗澡/剪毛/調理類為 true；
    // 剪指甲、局部修剪、除廢毛等單點加購項目為 false，維持原價不打折。
    // 與 performanceCategory（決定積分）完全獨立，不要混用同一套分類判斷。
    //
    // 注意（ddl-auto 陷阱）：新增到既有資料表的 NOT NULL 欄位，需給資料庫層級
    // 預設值，既有列才不會因無值可填導致 ALTER TABLE 失敗（比照 bookable 欄位）。
    // 這裡預設 true（多數項目屬於「洗澡/剪毛」類），啟動時 DataInitializer 會
    // 額外針對已知不打折的項目代碼做一次性修正，不論是全新安裝還是既有資料庫皆會生效。
    @Column(name = "discount_eligible", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean discountEligible = true;

    // 需求（追加）：僅限既有客戶——例如「貓咪基礎保養」這種低銷項目，店家規定
    // 不適用初次來店的寵物，這隻寵物完全沒有任何已結帳消費紀錄的話，這個項目
    // 不能被選（預約/開單畫面上直接濾掉，後端送出時也會再擋一次）。
    // 跟 discountEligible 一樣是新增到既有資料表的 NOT NULL 欄位，給資料庫層級預設值。
    @Column(name = "requires_existing_customer", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean requiresExistingCustomer = false;

    // 需求（追加）：適用物種——貓咪預約只顯示貓的項目，狗狗預約只顯示狗的項目。
    // null = 兩種都適用（例如清潔費這類共用加購項目），允許 null 不用給資料庫層級預設值。
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_pet_type")
    private com.petgrooming.pet_system.enums.PetType applicablePetType;

    // 需求（追加）：菜單簡化——貓咪套餐項目（CAT001~024）依「單層毛/雙層毛/長毛」
    // 進一步細分，LIFF 預約頁依所選毛孩的品種自動判斷結果篩選菜單，顧客只會看到
    // 符合自己貓咪毛髮分類的那 3 項套餐，不用在 24 項裡自己挑。
    // null = 跟毛髮分類無關的項目（貓咪加購 CAT025~028、所有狗狗項目、通用加購），
    // 這種項目不受這個欄位篩選影響，任何毛髮分類的貓都看得到（如果本身是貓咪適用的話）。
    @Enumerated(EnumType.STRING)
    @Column(name = "cat_coat_category")
    private com.petgrooming.pet_system.enums.CatCoatCategory catCoatCategory;

    // 需求（追加，2026-08-24）：狗狗定價流程簡化——DOG001~036 依體重級距標記，
    // LIFF 預約頁/店員開單頁依這隻狗目前的體重（Pet.weight）自動篩選對應級距的
    // 6 個項目（短毛/長毛 × 3 服務等級），不用把 36 項全部攤開。
    // null = 跟體重級距無關的項目（貓咪項目、通用加購），不受這個欄位篩選影響。
    @Enumerated(EnumType.STRING)
    @Column(name = "dog_weight_tier")
    private com.petgrooming.pet_system.enums.DogWeightTier dogWeightTier;
}