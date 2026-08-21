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
}