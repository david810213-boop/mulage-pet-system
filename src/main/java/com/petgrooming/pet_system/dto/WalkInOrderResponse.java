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
        private com.petgrooming.pet_system.enums.DiscountType appliedDiscountType; // 已結帳才有值；未結帳為 null

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
        res.setItems(o.getItems().stream().map(ItemLine::from).toList());
        return res;
    }
}
