package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 現場單項目的經手人與積分：經手人一旦被填上（開單當下直接填，或事後從待補清單補），
 * 就立刻依該項目快照的 performanceCategory／points 寫入績效紀錄；一筆項目只會計入一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkInOrderOperatorService {

    private final WalkInOrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;

    // ── 待補經手人清單（operatorStaff 為 null 的項目）──────────────
    // 只列出「有積分可算」的項目——零售商品加購（points 固定 0）
    // 不會產生績效，補了經手人也沒有計算意義，不需要出現在這份待辦清單裡。
    public List<WalkInOrderResponse.ItemLine> pendingOperatorItems() {
        List<WalkInOrderItem> pending = orderItemRepository.findByOperatorStaffIsNull();
        List<WalkInOrderResponse.ItemLine> result = new ArrayList<>();
        for (WalkInOrderItem oi : pending) {
            if (oi.getPoints() <= 0) continue;
            WalkInOrderResponse.ItemLine line = new WalkInOrderResponse.ItemLine();
            line.setItemId(oi.getId());
            line.setGroomingItemId(oi.getGroomingItemId());
            line.setItemName(oi.getItemName());
            line.setPrice(oi.getPrice());
            line.setPoints(oi.getPoints());
            line.setOperatorFilled(false);
            result.add(line);
        }
        return result;
    }

    // ── 補填某個項目的經手人（同步寫入績效紀錄）─────────────────────
    @Transactional
    public void fillOperator(Long orderItemId, Long staffId) {
        fillOperator(orderItemId, staffId, null);
    }

    // 待補經手人也可以一次選兩位
    @Transactional
    public void fillOperator(Long orderItemId, Long staffId, Long staffId2) {
        WalkInOrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到項目 #" + orderItemId));

        if (item.getOperatorStaff() != null) {
            throw new PaymentException("此項目已填寫經手人，無法重複填寫");
        }

        assignOperators(item, staffId, staffId2);
        orderItemRepository.save(item);

        awardPoints(item, item.getOrder());
        log.info("項目 #{} 補填經手人：{}{}", orderItemId, item.getOperatorStaff().getName(),
                item.getOperatorStaff2() != null ? "、" + item.getOperatorStaff2().getName() : "");
    }

    // 核對加項目時有選經手人的，存檔後立刻寫入積分
    // （用存檔後的項目清單逐一檢查，pointsAwarded 會擋掉已經計過的）
    void awardNewlyAssignedItems(WalkInOrder saved) {
        for (WalkInOrderItem it : saved.getItems()) {
            if (it.getOperatorStaff() != null && !it.isPointsAwarded()) {
                awardPoints(it, saved);
            }
        }
    }

    void assignOperators(WalkInOrderItem item, Long id1, Long id2) {
        if (id1 == null && id2 != null) {
            throw new PaymentException("請先選第一位經手人，再選第二經手人");
        }
        if (id1 != null && id1.equals(id2)) {
            throw new PaymentException("兩位經手人不能是同一個人");
        }
        item.setOperatorStaff(findOperator(id1));
        item.setOperatorStaff2(findOperator(id2));
    }

    private User findOperator(Long staffId) {
        if (staffId == null) return null;
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到員工 #" + staffId));
        if (!staff.isStaffOrAdmin()) {
            throw new PaymentException("經手人必須是店內員工");
        }
        return staff;
    }

    // ── 積分寫入：一筆項目只會被計入一次（pointsAwarded 防重複）────────────
    void awardPoints(WalkInOrderItem item, WalkInOrder order) {
        if (item.isPointsAwarded())
            return; // 防呆：避免同一筆被重複計分
        if (item.getPerformanceCategory() == PerformanceCategory.OTHER)
            return; // 跟預約結帳同樣規則，OTHER 不計分
        if (item.getPoints() <= 0)
            return;

        LocalDate serviceDate = order.getCreatedAt() != null
                ? order.getCreatedAt().toLocalDate()
                : LocalDate.now();

        if (item.getOperatorStaff2() != null) {
            // 雙人經手，積分各半
            performanceService.addDualRecords(
                    item.getOperatorStaff().getId(), item.getOperatorStaff2().getId(),
                    null, order.getId(), item.getPerformanceCategory(), item.getPoints(), serviceDate,
                    "現場單 #" + order.getId() + " - " + item.getItemName());
        } else {
            performanceService.addWalkInRecord(
                    item.getOperatorStaff().getId(),
                    order.getId(),
                    item.getPerformanceCategory(),
                    item.getPoints(),
                    serviceDate,
                    "現場單 #" + order.getId() + " - " + item.getItemName());
        }

        item.setPointsAwarded(true);
        orderItemRepository.save(item);
    }

    // ── 經手人積分結算報表（排除未填寫）────────────────────────────
    public List<OperatorPointsResponse> operatorPointsReport() {
        List<Object[]> rows = orderItemRepository.sumPointsByOperator();
        List<OperatorPointsResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            String operatorName = (String) r[1];
            double totalPoints = r[2] != null ? ((Number) r[2]).doubleValue() : 0;
            int totalAmount = r[3] != null ? ((Number) r[3]).intValue() : 0;
            long itemCount = r[4] != null ? ((Number) r[4]).longValue() : 0;
            result.add(new OperatorPointsResponse(operatorName, totalPoints, totalAmount, itemCount));
        }
        return result;
    }
}
