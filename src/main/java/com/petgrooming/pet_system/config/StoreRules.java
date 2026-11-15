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

    /** 線上預約只開放「今天＋此天數」以後的日期；更近的時段請顧客聯繫官方 LINE，由店員代客預約。 */
    public static final int ONLINE_BOOKING_MIN_DAYS_AHEAD = 3;

    // ── 預約訂金（店家公告《預約須知｜訂金及取消規範》，見 docs/adr/0009-booking-deposit.md）──

    /** 同一天預約兩位以上時，每位（首次大型犬除外）的訂金。 */
    public static final int DEPOSIT_PER_PET = 500;

    /** 首次體驗的大型犬，不論當天幾位都收這個金額。 */
    public static final int DEPOSIT_LARGE_DOG_FIRST_VISIT = 1000;

    /** 體重達到這個數值（公斤）的狗視為大型犬（對應體重級距的大型犬、特大型犬）。 */
    public static final double DEPOSIT_LARGE_DOG_MIN_KG = 23.0;

    /** 預約送出後必須在幾小時內付清訂金，逾期自動取消。 */
    public static final int DEPOSIT_PAYMENT_HOURS = 24;

    /** 距離預約開始至少這麼多小時取消，訂金全額退還；也是可以改期的最晚時間。 */
    public static final int DEPOSIT_FULL_REFUND_HOURS = 72;

    /** 距離預約開始至少這麼多小時（未達全額退款門檻）取消，退一半；更晚取消不退。 */
    public static final int DEPOSIT_HALF_REFUND_HOURS = 24;

    /** 已付訂金的預約可以改期的次數。 */
    public static final int DEPOSIT_MAX_RESCHEDULES = 1;
}
