package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 員工手機版「今日」頁面用的預約卡片資料（需求，2026-09-24）。
 * 只放畫面真正需要的欄位，不把整個 entity 丟給樣板。
 */
@Data
@Builder
public class MobileAppointmentCard {
    private Long id;
    private String dateLabel;   // 例如「9/25 週五」
    private String time;        // 例如「13:30」
    private String petName;
    private String petInitial;  // 頭像顯示的第一個字
    private String species;     // dog / cat / other，給 CSS 決定顏色
    private String speciesLabel;// 狗狗 / 貓咪 / 其他
    private String items;       // 服務項目，用「、」串起來
    private String ownerName;
    private String state;       // done / live / pending / normal
    private String statusLabel;
    private String note;        // 店家內部備注
    private int totalAmount;
    private boolean pending;    // 待確認，才顯示「確認預約」按鈕
    // 需求（2026-09-24 第二批）：流程階段，預約列表/詳情頁用
    // confirm 待確認 / checkin 待開單 / start 待開始 / serving 服務中 / check 待核對 /
    // pay 待結帳 / wire 待對帳 / done 已結帳 / cancelled 已取消
    private String stage;
    private String stageLabel;
}
