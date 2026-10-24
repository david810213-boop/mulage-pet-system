package com.petgrooming.pet_system.config;

/**
 * 店家規定的營運數值。數值來自店家要求，調整前先跟店家確認；
 * 同一個數值在多處使用時一律引用這裡，避免只改到其中一處。
 */
public final class StoreRules {

    private StoreRules() {
    }

    /** 單筆儲值下限。DTO 的 @Min 與錯誤訊息文字也要跟著改。 */
    public static final int MIN_DEPOSIT = 5000;

    /** 單筆儲值落在村民級距時，每次加贈的金額（店家確認不限第一次）。 */
    public static final int VILLAGE_DEPOSIT_BONUS = 200;

    /** 錢包餘額低於此值時，結帳預覽與後台會提醒店員。後台錢包頁的模板也寫了同一個數值。 */
    public static final int WALLET_LOW_BALANCE_THRESHOLD = 2000;

    /** 零售商品庫存小於等於此值視為低庫存。首頁模板也寫了同一個數值。 */
    public static final int LOW_STOCK_THRESHOLD = 5;

    /** 簽名圖片 data URL 的最短長度，太短代表只點了一下或沒簽。 */
    public static final int MIN_SIGNATURE_LENGTH = 1000;
}
