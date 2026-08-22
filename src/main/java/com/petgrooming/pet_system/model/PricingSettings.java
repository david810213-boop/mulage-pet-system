package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

// 需求（追加）：體重相關定價門檻，後台可調整，不寫死在程式碼裡。
// 只會有一列資料（單例設定），跟 CompanySignature 同樣的做法。
//
// 貓咪：體重加價目前是「參考金額」，由店員在核對/結帳時依店家截圖裡的門檻
// 自行判斷加收（透過既有的「新增服務項目」加購功能挑選對應的加價項目），
// 不是系統自動運算——這裡存的門檻數字提供畫面顯示參考，避免店員要背數字。
// 狗狗：小型犬／大型犬的體重切點依毛長而不同，一樣是參考數字，
// 用來判斷該幫這隻狗選哪個尺寸的服務項目。
@Entity
@Table(name = "pricing_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── 貓咪體重加價門檻（參考用金額） ──────────────────────────────
    @Builder.Default
    @Column(name = "cat_base_weight_limit")
    private double catBaseWeightLimit = 5.9; // 此體重（含）以下為基礎價，不加價

    @Builder.Default
    @Column(name = "cat_mid_weight_limit")
    private double catMidWeightLimit = 6.0; // 達此體重起加收 catMidSurcharge

    @Builder.Default
    @Column(name = "cat_mid_surcharge")
    private int catMidSurcharge = 200;

    @Builder.Default
    @Column(name = "cat_high_weight_limit")
    private double catHighWeightLimit = 8.0; // 達此體重起加收 catHighSurcharge（取代 mid，不是疊加）

    @Builder.Default
    @Column(name = "cat_high_surcharge")
    private int catHighSurcharge = 400;

    // ── 狗狗小型犬／大型犬體重切點（依毛長） ────────────────────────
    @Builder.Default
    @Column(name = "dog_long_coat_weight_limit")
    private double dogLongCoatWeightLimit = 17.0; // 長毛：此體重以下算小型犬

    @Builder.Default
    @Column(name = "dog_short_coat_weight_limit")
    private double dogShortCoatWeightLimit = 23.0; // 短毛／厚短毛：此體重以下算小型犬
}
