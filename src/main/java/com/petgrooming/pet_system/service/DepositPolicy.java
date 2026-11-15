package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.config.StoreRules;
import com.petgrooming.pet_system.enums.PetType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 店家公告《預約須知｜訂金及取消規範》的計算規則，不碰資料庫，方便單獨測試。
 * 設計取捨見 docs/adr/0009-booking-deposit.md。
 */
public final class DepositPolicy {

    private DepositPolicy() {
    }

    /** 取消時的訂金處理結果：退還金額與沒收金額，兩者相加等於取消前保留的訂金。 */
    public record Refund(int refund, int forfeited) {
    }

    /**
     * 同一會員同一天各筆有效預約的訂金份額，順序與傳入的旗標相同。
     * 首次體驗的大型犬固定 1,000；其他每位在當天有兩位以上時收 500（所以 2 位 1,000、3 位 1,500，以此類推）。
     *
     * @param largeFirstVisitFlags 每筆預約是否為首次體驗的大型犬
     * @param exempt               儲值金會員或店員免收：整天都不收訂金
     */
    public static List<Integer> shares(List<Boolean> largeFirstVisitFlags, boolean exempt) {
        int count = largeFirstVisitFlags.size();
        List<Integer> shares = new ArrayList<>(count);
        for (Boolean largeFirstVisit : largeFirstVisitFlags) {
            if (exempt) {
                shares.add(0);
            } else if (Boolean.TRUE.equals(largeFirstVisit)) {
                shares.add(StoreRules.DEPOSIT_LARGE_DOG_FIRST_VISIT);
            } else {
                shares.add(count >= 2 ? StoreRules.DEPOSIT_PER_PET : 0);
            }
        }
        return shares;
    }

    /** 體重未填時無法判斷，視為非大型犬（店員可在後台另外處理）。 */
    public static boolean isLargeDog(PetType petType, Double weightKg) {
        return petType == PetType.DOG && weightKg != null && weightKg >= StoreRules.DEPOSIT_LARGE_DOG_MIN_KG;
    }

    /**
     * 取消時保留的訂金怎麼處理：距離預約開始 72 小時以上退全額、24 小時以上退一半、更晚不退；
     * 店家因素取消一律全額退還。剛好 72 小時、24 小時算在退款較多的一側。
     */
    public static Refund refundOnCancel(int credit, LocalDateTime start, LocalDateTime cancelAt, boolean storeCause) {
        if (credit <= 0) {
            return new Refund(0, 0);
        }
        int refund;
        if (storeCause || !cancelAt.plusHours(StoreRules.DEPOSIT_FULL_REFUND_HOURS).isAfter(start)) {
            refund = credit;
        } else if (!cancelAt.plusHours(StoreRules.DEPOSIT_HALF_REFUND_HOURS).isAfter(start)) {
            refund = credit / 2;
        } else {
            refund = 0;
        }
        return new Refund(refund, credit - refund);
    }

    /** 已付訂金的預約，距離開始 72 小時以上、且還沒改期過，才可以改期。 */
    public static boolean canRescheduleWithDeposit(LocalDateTime start, LocalDateTime now, int rescheduleCount) {
        return rescheduleCount < StoreRules.DEPOSIT_MAX_RESCHEDULES
                && !now.plusHours(StoreRules.DEPOSIT_FULL_REFUND_HOURS).isAfter(start);
    }

    /** 付款期限：送出後 24 小時，但不晚於預約開始（店員代客預約的近期時段）。 */
    public static LocalDateTime paymentDueAt(LocalDateTime now, LocalDateTime start) {
        LocalDateTime due = now.plusHours(StoreRules.DEPOSIT_PAYMENT_HOURS);
        return due.isAfter(start) ? start : due;
    }
}
