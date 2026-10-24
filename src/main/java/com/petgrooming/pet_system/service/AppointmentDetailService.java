package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.Transaction;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.service.discount.DiscountCalculator;
import com.petgrooming.pet_system.service.discount.EligibleOffers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 預約明細（顧客 LIFF 與後台共用）：項目、實際套用的折扣標籤、實收金額、付款資訊與美容狀況照片。
 */
@Service
@RequiredArgsConstructor
public class AppointmentDetailService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final GroomingItemRepository groomingItemRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService; // 消費明細顯示實際套用的折扣種類需要會員折扣率
    private final DiscountCalculator discountCalculator;
    private final PetGroomingNoteRepository petGroomingNoteRepository;
    private final GroomingNotePhotoService groomingNotePhotoService;

    /** 預約明細（顧客 LIFF 與後台共用）。只有預約本人與員工可以查看。 */
    @Transactional(readOnly = true)
    public AppointmentDetailResponse getAppointmentDetail(Long appointmentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到使用者"));
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (!appointment.getUser().getId().equals(user.getId()) && !user.isStaffOrAdmin()) {
            throw new AppointmentException(ErrorCode.FORBIDDEN, "權限不足：只能查看自己的預約明細");
        }
        Optional<Transaction> transaction = transactionRepository.findByAppointmentId(appointmentId);

        var detail = AppointmentDetailResponse.builder()
                .appointmentCode(String.format("AP%03d", appointment.getId()))
                .petName(appointment.getPetName())
                .petType(appointment.getPetType())
                .date(appointment.getDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .statusLabel(appointment.getStatus().getLabel())
                .cancelled(appointment.isCancelled())
                .memberNote(appointment.getMemberNote())
                .items(detailItems(appointment, discountContext(appointment, transaction)))
                .totalAmount(appointment.getTotalAmount())
                .quotePending(appointment.isFirstVisitAssessment() && !appointment.isCheckinOrderConfirmed())
                .paid(appointment.isPaid());
        if (appointment.isFinalCheckDone()) {
            detail.groomingNote(appointment.getFinalCheckNote())
                    .groomingPhotos(groomingPhotoUrls(appointmentId));
        }
        // 帳面總額（totalAmount）與實收金額（chargedAmount）分開顯示，顧客才看得出打了多少折
        transaction.ifPresent(tx -> detail
                .paymentMethodLabel(tx.getPaymentMethod() != null ? tx.getPaymentMethod().getDisplayName() : null)
                .paymentTime(tx.getPaymentTime())
                .handledBy(tx.getHandledBy())
                .chargedAmount(tx.getFinalAmount()));
        return detail.build();
    }

    /**
     * 判斷各項目實際套用哪一種折扣所需的資訊。會員折扣只有儲值金付款才有，
     * 且與首次體驗、貓咪回洗擇優不疊加，因此要先知道這筆預約用什麼方式付款。
     */
    private record DiscountContext(boolean paid, boolean paidByWallet, double memberRate, EligibleOffers offers) {
    }

    private DiscountContext discountContext(Appointment appointment, Optional<Transaction> transaction) {
        boolean paidByWallet = transaction.map(tx -> tx.getPaymentMethod() == PaymentMethod.WALLET).orElse(false);
        double memberRate = paidByWallet
                ? walletService.getWallet(appointment.getUser().getUsername()).getDiscount()
                : 1.0;
        return new DiscountContext(transaction.isPresent(), paidByWallet, memberRate,
                discountCalculator.eligibleOffers(appointment));
    }

    /** 有到店開單項目時以開單項目為準（含經手人與加購商品），否則列出預約時選的項目。 */
    private List<AppointmentDetailResponse.DetailItem> detailItems(Appointment appointment, DiscountContext discounts) {
        List<AppointmentItem> checkinItems = appointmentItemRepository.findByAppointmentId(appointment.getId());
        if (checkinItems.isEmpty()) {
            return appointment.getSelectedItems().stream()
                    .map(item -> withDiscounts(AppointmentDetailResponse.DetailItem.builder()
                                    .groomingItemId(item.getId())
                                    .dogWeightTier(item.getDogWeightTier() != null ? item.getDogWeightTier().name() : null)
                                    .name(item.getName())
                                    .price((int) Math.round(item.getPrice())),
                            item.getPerformanceCategory(), item.isDiscountEligible(), discounts))
                    .toList();
        }
        Map<Long, GroomingItem> groomingItems = groomingItemRepository.findAllById(checkinItems.stream()
                        .map(AppointmentItem::getGroomingItemId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(GroomingItem::getId, item -> item));
        return checkinItems.stream()
                .map(ci -> {
                    GroomingItem source = ci.getGroomingItemId() != null ? groomingItems.get(ci.getGroomingItemId()) : null;
                    return withDiscounts(AppointmentDetailResponse.DetailItem.builder()
                                    .itemId(ci.getId())
                                    .groomingItemId(ci.getGroomingItemId())
                                    .dogWeightTier(source != null && source.getDogWeightTier() != null
                                            ? source.getDogWeightTier().name() : null)
                                    .name(ci.getItemName())
                                    .price(ci.getPrice())
                                    .operatorName(ci.getOperatorStaff() != null ? ci.getOperatorStaff().getName() : null)
                                    .retailItem(ci.getRetailProductId() != null),
                            ci.getPerformanceCategory(), memberDiscountEligible(ci, source), discounts);
                })
                .toList();
    }

    /**
     * 開單項目可否享會員折扣，跟結帳扣款（PaymentService）同一套規則：以服務項目目前的設定為準；
     * 零售商品、自訂項目（沒有對應服務項目）不打折；服務項目已被刪除時視為可打折。
     */
    private static boolean memberDiscountEligible(AppointmentItem item, GroomingItem source) {
        return item.getGroomingItemId() != null && (source == null || source.isDiscountEligible());
    }

    /**
     * 只回傳「實際套用哪一種折扣」的標籤，不重算金額（金額以結帳當下存入 Transaction.finalAmount 的為準），
     * 用跟結帳相同的擇優規則反推標籤；未結帳（尚未決定付款方式）則不填。
     */
    private AppointmentDetailResponse.DetailItem withDiscounts(AppointmentDetailResponse.DetailItem.DetailItemBuilder item,
                                                                PerformanceCategory category, boolean memberEligible,
                                                                DiscountContext discounts) {
        EligibleOffers offers = discounts.offers();
        return item
                .discountEligible(memberEligible)
                .rewashEligible(offers.applies(DiscountType.REWASH, category))
                .firstVisitEligible(offers.applies(DiscountType.FIRST_VISIT, category))
                .appliedDiscountType(discounts.paid()
                        ? offers.resolve(100.0, category, memberEligible && discounts.paidByWallet(),
                                discounts.memberRate()).type()
                        : null)
                .build();
    }

    /** 現場核對時留下的美容狀況照片（顧客在 LIFF 明細看得到）。 */
    private List<String> groomingPhotoUrls(Long appointmentId) {
        List<String> urls = new ArrayList<>();
        for (PetGroomingNote note : petGroomingNoteRepository.findByAppointmentId(appointmentId)) {
            urls.addAll(groomingNotePhotoService.photoUrls(note));
        }
        return urls;
    }
}
