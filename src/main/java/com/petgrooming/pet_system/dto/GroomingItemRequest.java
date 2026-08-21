package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GroomingItemRequest {
    
    @NotBlank(message = "項目代碼不能為空")
    @Pattern(regexp = "^GS\\d{3}$", message = "項目代碼格式必須為 GS 開頭加上 3 位數字，例如 GS013")
    private String itemCode;

    @NotBlank(message = "美容項目名稱不能為空")
    private String name;

    private String description;

    @NotNull(message = "價格不能為空")
    @Positive(message = "價格必須大於 0")
    private Double price;

    // 需求 4：是否可線上預約（大美容/小美容/精緻洗/定製洗 = true）
    private Boolean bookable = false;

    // 需求（追加）：是否可享會員儲值折扣/回洗優惠——像清潔費這種帶懲罰性質的附加費用，
    // 店家不希望被折扣掉，所以開放建立時就能決定要不要參與折扣，預設維持原本行為（可享）。
    private Boolean discountEligible = true;

    // 需求（追加）：可享折扣/回洗優惠/首次體驗優惠都要看這個分類判斷，
    // 之前的表單沒有開放選這欄，新增的項目一律預設「其他」（不計分），
    // 導致無法建立像「小型犬洗吹」這種需要對應正確分類的項目。
    private com.petgrooming.pet_system.enums.PerformanceCategory performanceCategory;

    // 需求（追加）：積分分類——同一個績效分類（例如 BATH_SMALL）現在會對應到很多不同
    // 價格的項目（貓咪/狗狗各種體型級距），如果積分都直接沿用分類的固定預設值，
    // 價差好幾倍的項目會拿到一樣的積分，不合理。開放店家自己指定這個項目對應
    // 哪一個積分分類，不填的話才退回用 performanceCategory 的預設積分（維持舊行為）。
    private Double points;

}