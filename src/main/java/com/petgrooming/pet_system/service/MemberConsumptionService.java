package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 會員消費紀錄彙整（需求，2026-09-24 第五批）。
 *
 * 原本整段寫在 CustomerAnalysisMvcController.customerDetail() 裡（網頁版會員信息頁），
 * 員工手機版的會員頁、毛孩頁也要顯示同一份消費紀錄；為了不把同一套彙整邏輯
 * 複製成兩份，原封不動搬到這裡，網頁版跟手機版都呼叫這支方法。內容沒有任何行為變更。
 *
 * 來源：預約結帳（Transaction，只取已付款）＋ 現場開單（WalkInOrder，只取已付款），
 * 依付款時間新到舊排序。
 */
@Service
@RequiredArgsConstructor
public class MemberConsumptionService {

    private final TransactionRepository transactionRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final AppointmentService appointmentService;
    private final WalkInOrderService walkInOrderService;

    /**
     * @param username       會員帳號
     * @param viewerUsername 查看的店家／員工帳號（查預約明細時需要權限判斷用），null 時不帶預約明細
     */
    public List<ConsumptionRecordResponse> buildPaidRecords(String username, String viewerUsername) {
        java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse> records = new java.util.ArrayList<>();

        // 來源 1：預約結帳（Transaction）
        for (var t : transactionRepository.findByUserUsername(username)) {
            if (!t.isPaid())
                continue;
            java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse.Item> items = java.util.List.of();
            if (t.getAppointment() != null && viewerUsername != null) {
                try {
                    var detail = appointmentService.getAppointmentDetail(t.getAppointment().getId(), viewerUsername);
                    items = detail.getItems().stream()
                            .map(di -> com.petgrooming.pet_system.dto.ConsumptionRecordResponse.Item.builder()
                                    .name(di.getName())
                                    .staffName(di.getOperatorName())
                                    .discountLabel(di.getAppliedDiscountType() != null ? di.getAppliedDiscountType().getLabel() : null)
                                    .price(di.getPrice())
                                    .build())
                            .toList();
                } catch (Exception ignored) {
                    // 找不到明細（例如舊資料）就顯示空清單，不影響整頁其他內容
                }
            }
            records.add(com.petgrooming.pet_system.dto.ConsumptionRecordResponse.builder()
                    .sourceLabel("預約結帳")
                    .sourceType("APPOINTMENT")
                    .recordId(t.getAppointment() != null ? t.getAppointment().getId() : null)
                    .code(t.getAppointment() != null ? String.format("AP%03d", t.getAppointment().getId()) : "—")
                    .petName(t.getAppointment() != null ? t.getAppointment().getPetName() : "—")
                    .time(t.getPaymentTime())
                    .handledBy(t.getHandledBy())
                    .paymentMethodLabel(t.getPaymentMethod() != null ? t.getPaymentMethod().getDisplayName() : "—")
                    .amount(t.getFinalAmount())
                    .paid(true)
                    .items(items)
                    .build());
        }

        // 來源 2：現場開單（WalkInOrder）—— 之前沒有併入會員信息頁，這次補上
        for (var w : walkInOrderRepository.findByMemberUsernameOrderByCreatedAtDesc(username)) {
            if (!w.isPaid())
                continue;
            java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse.Item> items = java.util.List.of();
            try {
                var detail = walkInOrderService.getById(w.getId());
                items = detail.getItems().stream()
                        .map(il -> com.petgrooming.pet_system.dto.ConsumptionRecordResponse.Item.builder()
                                .name(il.getItemName())
                                .staffName(il.getOperator())
                                .discountLabel(il.getAppliedDiscountType() != null ? il.getAppliedDiscountType().getLabel() : null)
                                .price(il.getPrice())
                                .build())
                        .toList();
            } catch (Exception ignored) {
                // 找不到明細就顯示空清單，不影響整頁其他內容
            }
            records.add(com.petgrooming.pet_system.dto.ConsumptionRecordResponse.builder()
                    .sourceLabel("現場開單")
                    .sourceType("WALKIN")
                    .recordId(w.getId())
                    .code("現場單#" + w.getId())
                    .petName(w.getPetName())
                    .time(w.getPaymentTime())
                    .handledBy(w.getCreatedBy())
                    .paymentMethodLabel(w.getPaymentMethod() != null ? w.getPaymentMethod().getDisplayName() : "—")
                    .amount(w.getTotalAmount())
                    .paid(true)
                    .items(items)
                    .build());
        }

        records.sort((a, b) -> {
            if (a.getTime() == null && b.getTime() == null)
                return 0;
            if (a.getTime() == null)
                return 1;
            if (b.getTime() == null)
                return -1;
            return b.getTime().compareTo(a.getTime());
        });
        return records;
    }

    /**
     * 交易紀錄列表用（需求，2026-09-24 第八批）：把「預約交易」跟「現場單」合成同一份清單，
     * 只取已付款，依付款時間新到舊排序。原本是 PaymentMvcController 的 private 方法，
     * 員工手機版交易紀錄頁也要用，原封不動搬到這裡共用，內容沒有任何行為變更。
     */
    public java.util.List<ConsumptionRecordResponse> buildCombinedRecords(
            java.util.List<com.petgrooming.pet_system.dto.TransactionResponse> transactions,
            java.util.List<com.petgrooming.pet_system.model.WalkInOrder> walkInOrders) {

        java.util.List<ConsumptionRecordResponse> records = new java.util.ArrayList<>();

        for (var t : transactions) {
            if (!t.isPaid()) continue;
            records.add(ConsumptionRecordResponse.builder()
                    .sourceLabel("預約結帳")
                    .sourceType("APPOINTMENT")
                    .recordId(t.getAppointmentId())
                    .code(t.getAppointmentCode())
                    .petName(t.getPetName())
                    .time(t.getPaymentTime())
                    .handledBy(t.getHandledBy())
                    .paymentMethodLabel(t.getPaymentMethod() != null ? t.getPaymentMethod().getDisplayName() : "—")
                    .amount(t.getFinalAmount())
                    .paid(true)
                    .build());
        }

        for (var w : walkInOrders) {
            if (!w.isPaid()) continue;
            records.add(ConsumptionRecordResponse.builder()
                    .sourceLabel("現場開單")
                    .sourceType("WALKIN")
                    .recordId(w.getId())
                    .code("現場單#" + w.getId())
                    .petName(w.getPetName())
                    .time(w.getPaymentTime())
                    .handledBy(w.getCreatedBy())
                    .paymentMethodLabel(w.getPaymentMethod() != null ? w.getPaymentMethod().getDisplayName() : "—")
                    .amount(w.getTotalAmount())
                    .paid(true)
                    .build());
        }

        records.sort((a, b) -> {
            if (a.getTime() == null && b.getTime() == null) return 0;
            if (a.getTime() == null) return 1;
            if (b.getTime() == null) return -1;
            return b.getTime().compareTo(a.getTime());
        });
        return records;
    }
}
