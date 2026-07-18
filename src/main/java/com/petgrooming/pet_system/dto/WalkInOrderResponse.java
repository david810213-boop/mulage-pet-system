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
    private String createdBy;
    private String note;
    private LocalDateTime createdAt;
    private boolean paid;
    private String paymentMethodLabel;
    private LocalDateTime paymentTime;
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
        res.setCreatedBy(o.getCreatedBy());
        res.setNote(o.getNote());
        res.setCreatedAt(o.getCreatedAt());
        res.setPaid(o.isPaid());
        res.setPaymentMethodLabel(o.getPaymentMethod() != null ? o.getPaymentMethod().getDisplayName() : null);
        res.setPaymentTime(o.getPaymentTime());
        res.setItems(o.getItems().stream().map(ItemLine::from).toList());
        return res;
    }
}
