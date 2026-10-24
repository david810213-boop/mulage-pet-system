package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.FinancialReportResponse;
import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.TopUpRequest;
import com.petgrooming.pet_system.model.Transaction;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import com.petgrooming.pet_system.repository.SupplyUsageRecordRepository;
import com.petgrooming.pet_system.repository.TopUpRequestRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * 財務報表（ADMIN）：當日與當月的業績、儲值、零售營收與成本、折扣統計、交易明細。
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final TransactionRepository transactionRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final TopUpRequestRepository topUpRequestRepository;
    private final RetailProductRepository retailProductRepository;
    private final SupplyUsageRecordRepository supplyUsageRecordRepository;

    /**
     * 當日與當月的業績、儲值、零售營收與成本。
     * 業績只算已結帳的預約與現場單（匯款待對帳不算）；儲值是預收款，另列不計入業績。
     */
    @Transactional(readOnly = true)
    public FinancialReportResponse getFinancialReport() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        List<PaidOrder> orders = paidOrders(retailUnitCosts());
        List<TopUpRequest> topUps = topUpRequestRepository.findByStatusOrderByCreatedAtAsc(TopUpStatus.CONFIRMED);
        int monthRetailCost = sumSince(orders, monthStart, PaidOrder::retailCost);
        int monthSupplyCost = supplyUsageRecordRepository.findByUsedAtBetween(monthStart, now).stream()
                .mapToInt(u -> u.getUnitCostSnapshot() * u.getQuantity())
                .sum();
        int monthRevenue = sumSince(orders, monthStart, PaidOrder::amount);

        FinancialReportResponse report = new FinancialReportResponse();
        report.setGeneratedAt(now);
        report.setTodayRevenueTotal(sumSince(orders, todayStart, PaidOrder::amount));
        report.setTodayRevenueWallet(sumSince(orders, todayStart, PaidOrder::walletAmount));
        report.setTodayRevenueNonWallet(sumSince(orders, todayStart, PaidOrder::nonWalletAmount));
        report.setTodayOrderCount(sumSince(orders, todayStart, o -> 1));
        report.setTodayTopupCollected(topUpsSince(topUps, todayStart));
        report.setTodayRetailRevenue(sumSince(orders, todayStart, PaidOrder::retailRevenue));
        report.setMonthRevenueTotal(monthRevenue);
        report.setMonthRevenueWallet(sumSince(orders, monthStart, PaidOrder::walletAmount));
        report.setMonthRevenueNonWallet(sumSince(orders, monthStart, PaidOrder::nonWalletAmount));
        report.setMonthOrderCount(sumSince(orders, monthStart, o -> 1));
        report.setMonthTopupCollected(topUpsSince(topUps, monthStart));
        report.setMonthRetailRevenue(sumSince(orders, monthStart, PaidOrder::retailRevenue));
        report.setMonthRetailCostEstimate(monthRetailCost);
        report.setMonthSupplyCost(monthSupplyCost);
        report.setMonthTotalCost(monthRetailCost + monthSupplyCost);
        report.setMonthEstimatedProfit(monthRevenue - monthRetailCost - monthSupplyCost);
        report.setTodayDiscounts(discountSummary(orders, todayStart));
        report.setMonthDiscounts(discountSummary(orders, monthStart));
        report.setDetails(orders.stream()
                .sorted(Comparator.comparing(PaidOrder::paidAt).reversed())
                .map(PaidOrder::toDetailLine)
                .toList());
        return report;
    }

    /**
     * 一筆已結帳的訂單（預約結帳或現場單）。
     * 零售營收以售價加總；零售成本以商品「目前」進貨單價估算（賣出當下沒有成本快照，因此是近似值）。
     */
    private record PaidOrder(String source, String code, LocalDateTime paidAt, PaymentMethod method,
                             int baseAmount, int amount, String handledBy, int retailRevenue, int retailCost) {

        /** 實收低於帳面金額時，回傳實收占帳面的百分比（四捨五入到整數）；沒有折扣時回傳空值。 */
        Optional<Integer> discountPercent() {
            if (baseAmount <= 0 || amount >= baseAmount) {
                return Optional.empty();
            }
            return Optional.of((int) Math.round(amount * 100.0 / baseAmount));
        }

        int walletAmount() {
            return method == PaymentMethod.WALLET ? amount : 0;
        }

        int nonWalletAmount() {
            return method == PaymentMethod.WALLET ? 0 : amount;
        }

        FinancialReportResponse.RevenueDetailLine toDetailLine() {
            return new FinancialReportResponse.RevenueDetailLine(source, code, paidAt,
                    method != null ? method.getDisplayName() : "—", method == PaymentMethod.WALLET, amount, handledBy);
        }
    }

    private List<PaidOrder> paidOrders(Map<Long, Integer> retailUnitCosts) {
        List<Transaction> transactions = transactionRepository.findByPaidTrue().stream()
                .filter(t -> t.getPaymentTime() != null)
                .toList();
        Map<Long, List<AppointmentItem>> itemsByAppointment = appointmentItemRepository.findByAppointmentIdIn(
                        transactions.stream().filter(t -> t.getAppointment() != null)
                                .map(t -> t.getAppointment().getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(item -> item.getAppointment().getId()));

        List<PaidOrder> orders = new ArrayList<>();
        for (Transaction t : transactions) {
            List<AppointmentItem> retailItems = t.getAppointment() == null ? List.of()
                    : itemsByAppointment.getOrDefault(t.getAppointment().getId(), List.of()).stream()
                            .filter(i -> i.getRetailProductId() != null).toList();
            orders.add(new PaidOrder("預約結帳",
                    t.getAppointment() != null ? String.format("AP%03d", t.getAppointment().getId()) : "—",
                    t.getPaymentTime(), t.getPaymentMethod(), t.getBaseAmount(), t.getFinalAmount(), t.getHandledBy(),
                    retailItems.stream().mapToInt(AppointmentItem::getPrice).sum(),
                    estimatedCost(retailItems.stream().map(AppointmentItem::getRetailProductId).toList(), retailUnitCosts)));
        }
        for (WalkInOrder w : walkInOrderRepository.findAll()) {
            if (!w.isPaid() || w.getPaymentTime() == null) continue;
            List<WalkInOrderItem> retailItems = w.getItems().stream()
                    .filter(i -> i.getRetailProductId() != null).toList();
            orders.add(new PaidOrder("現場開單", "現場單#" + w.getId(), w.getPaymentTime(), w.getPaymentMethod(),
                    w.getTotalAmount(), w.getChargedAmount() != null ? w.getChargedAmount() : w.getTotalAmount(), w.getCreatedBy(),
                    retailItems.stream().mapToInt(WalkInOrderItem::getPrice).sum(),
                    estimatedCost(retailItems.stream().map(WalkInOrderItem::getRetailProductId).toList(), retailUnitCosts)));
        }
        return orders;
    }

    private Map<Long, Integer> retailUnitCosts() {
        Map<Long, Integer> costs = new HashMap<>();
        retailProductRepository.findAll().forEach(p -> costs.put(p.getId(), p.getUnitCost()));
        return costs;
    }

    private static int estimatedCost(List<Long> retailProductIds, Map<Long, Integer> unitCosts) {
        return retailProductIds.stream().mapToInt(id -> unitCosts.getOrDefault(id, 0)).sum();
    }

    private static int sumSince(List<PaidOrder> orders, LocalDateTime from, ToIntFunction<PaidOrder> value) {
        return orders.stream().filter(o -> !o.paidAt().isBefore(from)).mapToInt(value).sum();
    }

    /**
     * 期間內有折扣的訂單（實收低於帳面），依折數分組，折扣少的排前面（例如 95折、9折、88折）。
     * 一張訂單只有部分項目打折時，折數是整張單的實收比例，可能出現 93折 這類非整數折扣。
     */
    private static List<FinancialReportResponse.DiscountSummary> discountSummary(List<PaidOrder> orders, LocalDateTime from) {
        Map<Integer, List<PaidOrder>> byPercent = orders.stream()
                .filter(o -> !o.paidAt().isBefore(from) && o.discountPercent().isPresent())
                .collect(Collectors.groupingBy(o -> o.discountPercent().get()));
        return byPercent.entrySet().stream()
                .sorted(Map.Entry.<Integer, List<PaidOrder>>comparingByKey().reversed())
                .map(e -> new FinancialReportResponse.DiscountSummary(
                        discountLabel(e.getKey()),
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().mapToInt(o -> o.baseAmount() - o.amount()).sum()))
                .toList();
    }

    /** 90 → 「9折」、88 → 「88折」。 */
    static String discountLabel(int percent) {
        return (percent % 10 == 0 ? String.valueOf(percent / 10) : String.valueOf(percent)) + "折";
    }

    private static int topUpsSince(List<TopUpRequest> topUps, LocalDateTime from) {
        return topUps.stream()
                .filter(t -> t.getReviewedAt() != null && !t.getReviewedAt().isBefore(from))
                .mapToInt(TopUpRequest::getAmount)
                .sum();
    }
}
