package com.petgrooming.pet_system.config;

import java.time.LocalTime;

/**
 * 營業時間與時段格線（開店、打烊、每格長度），預約名額與「預設時段容量範本」共用同一份格線。
 *
 * ⚠️ 這裡只決定「系統會產生哪些時段格線」（例如每 30 分鐘一格），
 * 不是「每個時段可以接幾組」——那是 DefaultSlotCapacityTemplate 資料表的事。
 */
public final class BusinessHours {

    private BusinessHours() {
        // 純常數類別，不需要實例化
    }

    /** 開始營業時間 */
    public static final LocalTime OPENING = LocalTime.of(11, 0);

    /** 結束營業時間（最後一個時段格線的終點，不代表最後一組收客時間） */
    public static final LocalTime CLOSING = LocalTime.of(19, 0);

    /**
     * 每個時段格線的長度（分鐘）。30 分鐘一格，讓店家可以用「預設時段容量範本」
     * 對每個 30 分鐘區間分別設定可接組數。
     */
    public static final int SLOT_MINUTES = 30;
}
