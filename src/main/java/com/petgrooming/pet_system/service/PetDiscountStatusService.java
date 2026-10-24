package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetDiscountStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * LIFF 預約頁顯示的寵物折扣資格：距上次洗澡幾天、優惠期內或已過期，
 * 以及服務項目要不要顯示「原價劃掉＋折扣價」。
 *
 * 三種折扣互斥擇優：首次體驗優惠與 90 天定期養護禮遇（貓咪回洗優惠）兩者本身也互斥，
 * 跟會員儲值折扣分開回傳，前端渲染時自己取較優惠者，邏輯跟結帳時的 DiscountCalculator.resolve() 一致。
 */
@Service
@RequiredArgsConstructor
public class PetDiscountStatusService {

    private final PetService petService;
    private final PetConsumptionHistoryService petConsumptionHistoryService;
    private final CatRewashDiscountService catRewashDiscountService;
    private final WalletService walletService;

    /**
     * @param referenceDate 計算距上次洗澡天數的基準日：應該用顧客實際選的預約日期，
     *                      不是永遠用今天——例如選了好幾個月後的日期，到那天早就超過 90 天，不該顯示符合資格
     */
    // 不能用 readOnly：顧客還沒有錢包時，walletService.getWallet() 會順便建立一個
    @Transactional
    public PetDiscountStatusResponse statusFor(Long petId, String username, LocalDate referenceDate) {
        petService.assertOwnership(petId, username);
        var pet = petService.getPetEntity(petId);
        var owner = pet.getOwner();
        String petType = pet.getPetType().name();
        boolean isCat = "CAT".equals(petType);
        boolean isDog = "DOG".equals(petType);

        boolean firstVisitEligible = !petConsumptionHistoryService.hasPriorPaidService(owner.getId(), pet.getName(), null);

        boolean rewashEligible = false;
        Long lastBathDaysAgo = null;
        if (isCat) {
            var lastBath = catRewashDiscountService.findLastBathDate(owner.getId(), pet.getName());
            if (lastBath.isPresent()) {
                lastBathDaysAgo = ChronoUnit.DAYS.between(lastBath.get(), referenceDate);
                rewashEligible = lastBathDaysAgo >= 0 && lastBathDaysAgo < CatRewashDiscountService.REWASH_WINDOW_DAYS;
            }
        }

        double memberDiscountRate = walletService.getWallet(owner.getUsername()).getDiscount();
        SpecialOffer offer = specialOffer(isCat, isDog, firstVisitEligible, rewashEligible);
        return new PetDiscountStatusResponse(petType, firstVisitEligible, rewashEligible, lastBathDaysAgo,
                memberDiscountRate, offer.label(), offer.rate(), offer.categories());
    }

    private record SpecialOffer(String label, Double rate, List<String> categories) {
        static final SpecialOffer NONE = new SpecialOffer(null, null, List.of());
    }

    /**
     * 首次體驗跟定期養護禮遇互斥，首次體驗優先（跟結帳時的判斷順序一致：先看是不是首次消費）。
     * 顧客端一律稱「定期養護禮遇」，後台管理頁維持「回洗優惠」的用語。
     */
    private static SpecialOffer specialOffer(boolean isCat, boolean isDog, boolean firstVisitEligible, boolean rewashEligible) {
        if (isCat && firstVisitEligible) {
            return new SpecialOffer("首次體驗優惠", CatFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE,
                    List.of("BATH_CAT_S", "BATH_CAT_L"));
        }
        if (isDog && firstVisitEligible) {
            return new SpecialOffer("首次體驗優惠", DogFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE,
                    List.of("BATH_SMALL", "BATH_LARGE"));
        }
        if (isCat && rewashEligible) {
            return new SpecialOffer("定期養護禮遇", CatRewashDiscountService.REWASH_DISCOUNT_RATE,
                    List.of("BATH_CAT_S", "BATH_CAT_L"));
        }
        return SpecialOffer.NONE;
    }
}
