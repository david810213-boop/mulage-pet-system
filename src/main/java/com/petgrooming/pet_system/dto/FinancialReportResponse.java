package com.petgrooming.pet_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求 6：財務報表。
 *
 * 業績定義（跟店家確認過的規則，寫死在這裡避免以後不小心弄混）：
 *   - 「儲值」本身只是預收款，不算業績，不會出現在 revenue 相關欄位裡
 *   - 只有「結帳完成」（預約結帳 Transaction / 現場單 WalkInOrder，paid=true）才算業績
 *   - 儲值總額另外用 topupCollected 欄位單獨呈現，讓店家看得到現金流但不會誤會成業績
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportResponse {

    private LocalDateTime generatedAt;

    // ── 當日 ──────────────────────────────────────────────────────────
    private int todayRevenueTotal;       // 當日總業績（預約結帳 + 現場單，不含儲值）
    private int todayRevenueWallet;      // 當日業績中，用儲值金扣款的部分
    private int todayRevenueNonWallet;   // 當日業績中，非儲值金付款的部分（現金/LinePay/匯款）
    private int todayOrderCount;         // 當日結帳筆數（預約+現場單合計）
    private int todayTopupCollected;     // 當日儲值總額（預收款，不計入業績，僅供參考）
    private int todayRetailRevenue;      // 需求（追加）：當日零售商品營收（已含在 todayRevenueTotal 業績裡，這裡單獨拆出來看）

    // ── 當月 ──────────────────────────────────────────────────────────
    private int monthRevenueTotal;
    private int monthRevenueWallet;
    private int monthRevenueNonWallet;
    private int monthOrderCount;
    private int monthTopupCollected;
    private int monthRetailRevenue;      // 需求（追加）：當月零售商品營收（已含在 monthRevenueTotal 業績裡）

    // ── 成本（需求 7 庫存資料帶入，當月）──────────────────────────────────
    private int monthRetailCostEstimate;  // 零售商品成本估算（用目前進貨單價回推，非逐筆歷史成本）
    private int monthSupplyCost;          // 店用洗劑領用成本（有逐筆單價快照，精確）
    private int monthTotalCost;           // 上面兩項合計
    private int monthEstimatedProfit;     // monthRevenueTotal - monthTotalCost（粗估毛利，未計入人事/房租等固定成本）

    // ── 明細（供頁面表格顯示 / Excel 匯出）───────────────────────────────
    private List<RevenueDetailLine> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueDetailLine {
        private String sourceLabel;   // 「預約結帳」或「現場開單」
        private String code;
        private LocalDateTime paymentTime;
        private String paymentMethodLabel;
        private boolean walletPayment;
        private int amount;
        private String handledBy;
    }
}
