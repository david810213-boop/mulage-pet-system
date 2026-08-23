package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PendingOperatorMatrixResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.WalkInOrderItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 需求（追加）：矩陣式待補經手人表單。取代原本一列一項目的扁平清單，
 * 改成「一隻寵物一列、橫向固定欄位」的表格，本次消費沒出現的項目那一格直接顯示「無」，
 * 只有實際有這個項目、還沒填經手人的格子才會出現下拉選單。
 */
@Service
@RequiredArgsConstructor
public class PendingOperatorMatrixService {

    private final WalkInOrderItemRepository walkInOrderItemRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final AppointmentRepository appointmentRepository;

    // 矩陣固定欄位順序：跟店家截圖的洗澡/吹毛/基美/剪毛/AD/HC/局部修剪/特殊項目一致，
    // 特殊項目多留一欄（SPECIAL2）給重疊的特殊項目用。CHECKIN/CHECKOUT/COMPLETE 是內部
    // 流程標記、不會真的變成計價項目，不用列進來。
    public static final List<PerformanceCategory> COLUMNS = List.of(
            PerformanceCategory.BATH_SMALL, PerformanceCategory.BATH_LARGE,
            PerformanceCategory.BATH_CAT_S, PerformanceCategory.BATH_CAT_L,
            PerformanceCategory.BLOW_SMALL, PerformanceCategory.BLOW_LARGE,
            PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BLOW_CAT_L,
            PerformanceCategory.BASIC, PerformanceCategory.TRIM,
            PerformanceCategory.AD, PerformanceCategory.HC,
            PerformanceCategory.PARTIAL, PerformanceCategory.SPECIAL, PerformanceCategory.SPECIAL2);

    @Transactional(readOnly = true)
    public List<PendingOperatorMatrixResponse> buildWalkInMatrix() {
        List<PendingOperatorMatrixResponse> result = new ArrayList<>();
        Map<Long, List<WalkInOrderItem>> byOrder = walkInOrderItemRepository.findByOperatorStaffIsNull()
                .stream()
                .filter(i -> i.getPoints() > 0)
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        for (var entry : byOrder.entrySet()) {
            var order = walkInOrderRepository.findById(entry.getKey()).orElse(null);
            if (order == null)
                continue;
            result.add(PendingOperatorMatrixResponse.builder()
                    .sourceLabel("現場開單")
                    .code("現場單#" + order.getId())
                    .petName(order.getPetName())
                    .cells(buildCellsForWalkIn(entry.getValue()))
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PendingOperatorMatrixResponse> buildAppointmentMatrix() {
        List<PendingOperatorMatrixResponse> result = new ArrayList<>();
        Map<Long, List<AppointmentItem>> byAppointment = appointmentItemRepository.findByOperatorStaffIsNull()
                .stream()
                .filter(i -> i.getPoints() > 0)
                .collect(Collectors.groupingBy(i -> i.getAppointment().getId()));
        for (var entry : byAppointment.entrySet()) {
            var appointment = appointmentRepository.findById(entry.getKey()).orElse(null);
            if (appointment == null)
                continue;
            result.add(PendingOperatorMatrixResponse.builder()
                    .sourceLabel("預約現場開單")
                    .code(String.format("AP%03d", appointment.getId()))
                    .petName(appointment.getPetName())
                    .cells(buildCellsForAppointment(entry.getValue()))
                    .build());
        }
        return result;
    }

    // 把同一張單「未填經手人」的項目，依 performanceCategory 塞進固定欄位；
    // 同一格如果重複出現（例如兩個特殊項目），第二個溢出到 SPECIAL2。
    private List<PendingOperatorMatrixResponse.Cell> buildCellsForWalkIn(List<WalkInOrderItem> items) {
        Map<PerformanceCategory, WalkInOrderItem> assigned = new EnumMap<>(PerformanceCategory.class);
        assignWithOverflow(items, WalkInOrderItem::getPerformanceCategory, assigned);

        List<PendingOperatorMatrixResponse.Cell> cells = new ArrayList<>();
        for (PerformanceCategory col : COLUMNS) {
            WalkInOrderItem item = assigned.get(col);
            cells.add(item == null
                    ? PendingOperatorMatrixResponse.Cell.builder().build()
                    : PendingOperatorMatrixResponse.Cell.builder()
                            .itemId(item.getId()).price(item.getPrice()).points(item.getPoints()).build());
        }
        return cells;
    }

    private List<PendingOperatorMatrixResponse.Cell> buildCellsForAppointment(List<AppointmentItem> items) {
        Map<PerformanceCategory, AppointmentItem> assigned = new EnumMap<>(PerformanceCategory.class);
        assignWithOverflow(items, AppointmentItem::getPerformanceCategory, assigned);

        List<PendingOperatorMatrixResponse.Cell> cells = new ArrayList<>();
        for (PerformanceCategory col : COLUMNS) {
            AppointmentItem item = assigned.get(col);
            cells.add(item == null
                    ? PendingOperatorMatrixResponse.Cell.builder().build()
                    : PendingOperatorMatrixResponse.Cell.builder()
                            .itemId(item.getId()).price(item.getPrice()).points(item.getPoints()).build());
        }
        return cells;
    }

    private <T> void assignWithOverflow(List<T> items,
            java.util.function.Function<T, PerformanceCategory> categoryOf,
            Map<PerformanceCategory, T> assigned) {
        List<T> overflow = new ArrayList<>();
        for (T item : items) {
            PerformanceCategory cat = categoryOf.apply(item);
            if (cat == null)
                continue;
            if (!assigned.containsKey(cat)) {
                assigned.put(cat, item);
            } else {
                overflow.add(item);
            }
        }
        for (T item : overflow) {
            if (!assigned.containsKey(PerformanceCategory.SPECIAL2)) {
                assigned.put(PerformanceCategory.SPECIAL2, item);
            }
            // 再多溢出的極端狀況（同一張單 3 個以上同分類項目）先不處理，
            // 這種情況目前店家操作模式下不會發生。
        }
    }
}
