package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.CatCoatCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * 貓咪品種 → 毛髮分類對照表，後台可編輯（新品種、分類調整不用改程式碼重新部署）。
 * 新增寵物時，前端下拉選單直接列出這裡的品種名稱給顧客選；LIFF 選了品種之後，
 * 後端依這張表查出對應分類，寫進 Pet.catCoatCategory。
 */
@Entity
@Table(name = "cat_breed_coat_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatBreedCoatMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "breed_name", nullable = false, unique = true, length = 50)
    private String breedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "coat_category", nullable = false)
    private CatCoatCategory coatCategory;

    // 排序用（下拉選單顯示順序），數字越小越前面，同數字依品種名稱排序
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
