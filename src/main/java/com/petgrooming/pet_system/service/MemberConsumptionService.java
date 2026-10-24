package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.dto.TransactionResponse;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 會員消費紀錄彙整：網頁版會員信息頁與員工手機版的會員頁、毛孩頁共用。
 *
 * 來源：預約結帳（Transaction，只取已付款）＋ 現場開單（WalkInOrder，只取已付款），
 * 依付款時間新到舊排序。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberConsumptionService {

    private final TransactionRepository transactionRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final AppointmentDetailService appointmentDetailService;
    private final WalkInOrderService walkInOrderService;

    /**
     * @param username       會員帳號
     * @param viewerUsername 查看的店家／員工帳號（查預約明細時需要權限判斷用），null 時不帶預約明細
     */
    public List<ConsumptionRecordResponse> buildPaidRecords(String username, String viewerUsername) {
        List<ConsumptionRecordResponse> records = new ArrayList<>();

        // 來源 1：預約結帳（Transaction）
        for (var t : transactionRepository.findByUserUsername(username)) {
            if (!t.isPaid())
                continue;
            List<ConsumptionRecordResponse.Item> items = List.of();
            if (t.getAppointment() != null && viewerUsername != null) {
                try {
                    var detail = appointmentDetailService.getAppointmentDetail(t.getAppointment().getId(), viewerUsername);
                    items = detail.getItems().stream()
                            .map(di -> ConsumptionRecordResponse.Item.builder()
                                    .name(di.getName())
                                    .staffName(di.getOperatorName())
                                    .discountLabel(di.getAppliedDiscountType() != null ? di.getAppliedDiscountType().getLabel() : null)
                                    .price(di.getPrice())
                                    .build())
                            .toList();
                } catch (Exception e) {
                    // 找不到明細（例如舊資料）就顯示空清單，不影響整頁其他內容
                    log.debug("預約 #{} 消費明細無法組出，顯示空清單：{}", t.getAppointment().getId(), e.getMessage());
                }
            }
            records.add(ConsumptionRecordResponse.builder()
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
            List<ConsumptionRecordResponse.Item> items = List.of();
            try {
                var detail = walkInOrderService.getById(w.getId());
                items = detail.getItems().stream()
                        .map(il -> ConsumptionRecordResponse.Item.builder()
                                .name(il.getItemName())
                                .staffName(il.getOperator())
                                .discountLabel(il.getAppliedDiscountType() != null ? il.getAppliedDiscountType().getLabel() : null)
                                .price(il.getPrice())
                                .build())
                        .toList();
            } catch (Exception e) {
                // 找不到明細就顯示空清單，不影響整頁其他內容
                log.debug("現場單 #{} 消費明細無法組出，顯示空清單：{}", w.getId(), e.getMessage());
            }
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
     * 交易紀錄列表用：把「預約交易」跟「現場單」合成同一份清單，
     * 只取已付款，依付款時間新到舊排序。網頁版與員工手機版的交易紀錄頁共用。
     */
    public List<ConsumptionRecordResponse> buildCombinedRecords(
            List<TransactionResponse> transactions,
            List<WalkInOrder> walkInOrders) {

        List<ConsumptionRecordResponse> records = new ArrayList<>();

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
