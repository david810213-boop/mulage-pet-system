package com.petgrooming.pet_system.enums;

import lombok.Getter;

/**
 * 貓咪毛髮分類（單層毛／雙層毛／長毛），用來簡化 LIFF 預約頁的菜單——
 * 顧客選毛孩時，系統依品種自動判斷這隻貓屬於哪個分類，預約頁只顯示
 * 對應分類的套餐項目，不用把 24 項全部攤開讓顧客自己挑，容易選錯。
 *
 * 跟 {@link CoatType}（短毛/長毛/中長毛，店家後台依實際毛況手動定義，
 * 影響美容時長估算）是兩個獨立的概念，不要混用：這個欄位是「品種帶出來的
 * 分類」，用途單純是自動篩菜單；CoatType 是店家自己看過寵物之後填的，
 * 兩者互不影響。
 *
 * SPECIAL：品種不在對照表裡（特殊貓種），系統無法自動判斷，LIFF 預約頁
 * 這種情況會顯示全部貓咪項目讓店家人工協助判斷，不會顯示「另外報價」
 * 字樣误導顧客以為要額外收費。
 */
@Getter
public enum CatCoatCategory {
    SINGLE_LAYER("單層毛"),
    DOUBLE_LAYER("雙層毛"),
    LONG_HAIR("長毛"),
    SPECIAL("特殊貓種（需店家協助判斷）");

    private final String label;

    CatCoatCategory(String label) {
        this.label = label;
    }
}
