package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 現場單回傳格式
 */
@Data
public class WalkInOrderResponse {
    private Long id;
    private String memberUsername;
    private String memberName;
    private String petName;
    private int totalAmount;
    private Integer chargedAmount; // 需求 5：實際扣款金額（打折後），null 代表跟 totalAmount 相同或尚未結帳
    private String createdBy;
    private String note;
    private LocalDateTime createdAt;
    private boolean paid;
    private String paymentMethodLabel;
    private LocalDateTime paymentTime;
    private boolean pendingWireTransfer; // 需求 15 修正：已選匯款但店家尚未確認收款（比照需求10 Appointment 的待對帳機制）
    private boolean serviceEndedDone;
    private boolean finalCheckDone;
    // 需求 7-1 修正：這張單是否含有美容服務項目——true 才需要走「結束服務→核對」流程，
    // 純零售商品訂單（false）可以直接結帳。
    private boolean requiresServiceFlow;
    private List<ItemLine> items;

    @Data
    public static class ItemLine {
        private Long itemId;
        private Long groomingItemId;
        private String itemName;
        private int price;
        private double points;
        private Long operatorStaffId;
        private String operator;        // 經手人姓名（顯示用）
        private boolean operatorFilled;
        private boolean discountEligible; // 需求 5：是否可享會員儲值金折扣
        // 需求 8 修正：回洗優惠與會員折扣只能擇一，消費明細要能看出「實際套用的是哪一種」
        private boolean rewashEligible; // 這個項目是否符合回洗優惠資格（僅限有綁定會員、且該會員的貓距上次洗澡未滿90天）
        private boolean firstVisitEligible; // 需求（追加）：這個項目是否符合狗狗首次體驗優惠資格
        private com.petgrooming.pet_system.enums.DiscountType appliedDiscountType; // 已結帳才有值；未結帳為 null
        private Long retailProductId; // 需求 7-1：非 null 代表這一列是零售商品加購
        private boolean retailItem;   // 方便前端判斷是不是零售商品（不用另外判斷 groomingItemId == null 這種間接方式）
        // 需求（追加，2026-08-24 修正）：這個項目本身的體重級距（SMALL~EXTRA_LARGE/null）。
        // 修正前，結帳頁的「鎖定為固定套餐」按鈕是拿「新增服務項目」下拉選單那份
        // 已經依目前體重篩過的清單去反查這張單裡的項目，如果狗的體重資料跟這張單
        // 當初選的體重級距對不上（例如體重還沒更新），會篩選失敗、鎖定按鈕誤判成
        // 「沒有可鎖定的項目」。改成讓每個項目自帶這個欄位，不用再跟會浮動的另一份
        // 清單比對，從根本解決這個問題。
        private String dogWeightTier;

        static ItemLine from(WalkInOrderItem oi) {
            ItemLine l = new ItemLine();
            l.setItemId(oi.getId());
            l.setGroomingItemId(oi.getGroomingItemId());
            l.setItemName(oi.getItemName());
            l.setPrice(oi.getPrice());
            l.setPoints(oi.getPoints());
            if (oi.getOperatorStaff() != null) {
                l.setOperatorStaffId(oi.getOperatorStaff().getId());
                l.setOperator(oi.getOperatorStaff().getName());
            }
            l.setOperatorFilled(oi.isOperatorFilled());
            l.setDiscountEligible(oi.isDiscountEligible());
            l.setRetailProductId(oi.getRetailProductId());
            l.setRetailItem(oi.getRetailProductId() != null);
            return l;
        }
    }

    public static WalkInOrderResponse from(WalkInOrder o) {
        WalkInOrderResponse res = new WalkInOrderResponse();
        res.setId(o.getId());
        if (o.getMember() != null) {
            res.setMemberUsername(o.getMember().getUsername());
            res.setMemberName(o.getMember().getName());
        }
        res.setPetName(o.getPetName());
        res.setTotalAmount(o.getTotalAmount());
        res.setChargedAmount(o.getChargedAmount());
        res.setCreatedBy(o.getCreatedBy());
        res.setNote(o.getNote());
        res.setCreatedAt(o.getCreatedAt());
        res.setPaid(o.isPaid());
        res.setPaymentMethodLabel(o.getPaymentMethod() != null ? o.getPaymentMethod().getDisplayName() : null);
        res.setPaymentTime(o.getPaymentTime());
        res.setPendingWireTransfer(
                o.getPaymentMethod() == com.petgrooming.pet_system.enums.PaymentMethod.WIRE_TRANSFER
                        && !o.isPaid());
        res.setServiceEndedDone(o.isServiceEndedDone());
        res.setFinalCheckDone(o.isFinalCheckDone());
        res.setRequiresServiceFlow(o.getItems().stream().anyMatch(i -> i.getGroomingItemId() != null));
        res.setItems(o.getItems().stream().map(ItemLine::from).toList());
        return res;
    }
}
