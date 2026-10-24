package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse.DetailItem;
import com.petgrooming.pet_system.dto.WalkInOrderResponse.ItemLine;
import com.petgrooming.pet_system.service.discount.DiscountCalculator;
import com.petgrooming.pet_system.service.discount.SpecialDiscountPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 結帳頁的儲值金預覽（預約結帳、現場單結帳共用）。
 *
 * 逐項目套用「首次體驗優惠／回洗優惠」與會員折扣擇一的規則（需求 8-1），
 * 讓結帳前看到的儲值金扣款金額跟實際結帳一致；狗狗與貓咪的首次體驗優惠分別由各自的 service 計算。
 */
@Service
@RequiredArgsConstructor
public class CheckoutPreviewService {

    private final WalletService walletService;
    private final CatRewashDiscountService catRewashDiscountService;
    private final DogFirstVisitDiscountService dogFirstVisitDiscountService;
    private final CatFirstVisitDiscountService catFirstVisitDiscountService;

    /** 結帳頁顯示的會員儲值金資訊與「用儲值金付款」的預估扣款金額。 */
    public record WalletPreview(Integer balance, boolean lowBalance, double discount,
                                boolean discountActive, int finalAmount) {
    }

    public WalletPreview forAppointment(String memberUsername, List<DetailItem> items, String petType) {
        return preview(memberUsername, petType, items.stream()
                .map(i -> new Line(i.getPrice(), i.isFirstVisitEligible(), i.isRewashEligible(), i.isDiscountEligible()))
                .toList());
    }

    public WalletPreview forWalkIn(String memberUsername, List<ItemLine> items, String petType) {
        return preview(memberUsername, petType, items.stream()
                .map(i -> new Line(i.getPrice(), i.isFirstVisitEligible(), i.isRewashEligible(), i.isDiscountEligible()))
                .toList());
    }

    private record Line(int price, boolean firstVisitEligible, boolean rewashEligible, boolean discountEligible) {
    }

    private WalletPreview preview(String memberUsername, String petType, List<Line> lines) {
        var wallet = walletService.getWallet(memberUsername);
        double discount = wallet.getDiscount();
        boolean isDog = "DOG".equalsIgnoreCase(petType);
        double total = lines.stream().mapToDouble(line -> linePrice(line, isDog, discount)).sum();
        return new WalletPreview(
                wallet.getBalance(),
                wallet.getBalance() < PaymentService.WALLET_LOW_BALANCE_THRESHOLD,
                discount,
                wallet.isCardActive() && discount < 1.0,
                (int) Math.round(total));
    }

    /** 跟實際結帳相同的擇優規則（DiscountCalculator）；首次體驗優惠依物種用狗狗或貓咪的規則。 */
    private double linePrice(Line line, boolean isDog, double discount) {
        SpecialDiscountPolicy special = line.firstVisitEligible()
                ? (isDog ? dogFirstVisitDiscountService : catFirstVisitDiscountService)
                : line.rewashEligible() ? catRewashDiscountService : null;
        return DiscountCalculator.resolve(line.price(), special, line.discountEligible(), discount).price();
    }
}
