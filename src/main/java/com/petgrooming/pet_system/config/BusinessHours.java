package com.petgrooming.pet_system.config;

import java.time.LocalTime;

/**
 * 需求（追加，2026-08-27）：時段容量範本功能新增。
 *
 * 原本營業時間／時段格線常數（OPENING/CLOSING/時段長度）寫死在 AppointmentService 裡，
 * 但新增的「預設時段容量範本」（DefaultSlotCapacityTemplateService）也需要同一份格線，
 * 才能知道範本要涵蓋哪些時間點。抽成這個共用常數類別，避免兩邊各自寫一份、以後改一邊忘了改另一邊。
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
     * 每個時段格線的長度（分鐘）。
     * 需求（2026-08-27）：從原本固定 2 小時（120分鐘）改成 30 分鐘一格，
     * 讓店家可以用「預設時段容量範本」對每個 30 分鐘區間分別設定可接組數。
     */
    public static final int SLOT_MINUTES = 30;
}
