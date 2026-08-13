package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CatRewashCandidateResponse;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 需求 8-1：貓咪 90 天回洗優惠。
 *
 * 規則（依需求文件逐字解讀）：
 *   「貓咪距上次洗澡未滿90天再預約洗澡，結帳自動套用9折；
 *    期間只做非洗澡單項服務視為中斷，重新計算。」
 *
 * 判斷邏輯：
 *   - 只看這隻貓「上一次實際完成的洗澡服務」日期（performanceCategory 為
 *     BATH_CAT_S / BATH_CAT_L 的項目），不是隨便一次到店服務都算。
 *   - 中間如果只做了非洗澡的單項服務（例如純剪指甲），並不會把「上次洗澡日期」
 *     往後推——本來就只看「上一次洗澡」，這句話是在澄清：不要誤把任何到店
 *     服務都當作洗澡處理、進而錯誤延續優惠資格。
 *   - 距離「上一次洗澡」未滿 90 天，這次若又有洗澡項目，該項目打 9 折。
 *
 * 資料來源涵蓋範圍（修正後）：
 *   「預約」（Appointment）與「現場開單」（WalkInOrder）都納入計算——但現場開單
 *   必須「有綁定會員」才算數（沒有會員身分的現場客沒有可追蹤的寵物身分/歷史）。
 *   Appointment 用 (user_id, petName) 配對識別同一隻貓；WalkInOrder 用
 *   (member_id, petName) 配對，兩邊都沿用需求 9 既有的「文字配對」慣例，
 *   因為兩張表都沒有直接關聯 Pet 實體。WalkInOrder 沒有獨立的服務日期欄位，
 *   以 createdAt 當天的日期視為服務日期。
 */
@Service
@RequiredArgsConstructor
public class CatRewashDiscountService {

    public static final int REWASH_WINDOW_DAYS = 90;
    public static final double REWASH_DISCOUNT_RATE = 0.9; // 9 折

    private static final Set<PerformanceCategory> CAT_BATH_CATEGORIES =
            EnumSet.of(PerformanceCategory.BATH_CAT_S, PerformanceCategory.BATH_CAT_L);

    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final PetRepository petRepository;

    // 這個服務項目是否屬於「貓咪洗澡」類別
    public boolean isCatBathItem(GroomingItem item) {
        return item != null && CAT_BATH_CATEGORIES.contains(item.getPerformanceCategory());
    }

    public boolean isCatBathCategory(PerformanceCategory category) {
        return CAT_BATH_CATEGORIES.contains(category);
    }

    /**
     * 這筆預約結帳時，是否符合「距上次洗澡未滿 90 天」優惠資格。
     * 只有貓咪（petType == CAT）才有可能符合。
     */
    public boolean isRewashEligible(Appointment appointment) {
        if (!"CAT".equalsIgnoreCase(appointment.getPetType())) return false;

        return findLastBathDate(appointment.getUser().getId(), appointment.getPetName(),
                appointment.getId(), appointment.getDate())
                .map(lastBathDate -> withinWindow(lastBathDate, appointment.getDate()))
                .orElse(false);
    }

    /**
     * 需求 8 修正：現場開單（有綁定會員）結帳時，是否符合回洗優惠資格。
     * 沒有綁定會員的現場單無法識別是哪隻寵物的歷史紀錄，一律不適用。
     */
    public boolean isRewashEligible(WalkInOrder order) {
        if (order.getMember() == null) return false;

        boolean isCat = petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .map(pet -> pet.getPetType() == PetType.CAT)
                .orElse(false);
        if (!isCat) return false;

        LocalDate serviceDate = order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : LocalDate.now();
        return findLastBathDate(order.getMember().getId(), order.getPetName(), order.getId(), serviceDate)
                .map(lastBathDate -> withinWindow(lastBathDate, serviceDate))
                .orElse(false);
    }

    private boolean withinWindow(LocalDate lastBathDate, LocalDate asOfDate) {
        long daysSince = ChronoUnit.DAYS.between(lastBathDate, asOfDate);
        return daysSince >= 0 && daysSince < REWASH_WINDOW_DAYS;
    }

    /**
     * 這隻貓「上一次完成洗澡服務」的日期，合併「預約」與「現場開單（有會員）」兩個來源取最大值。
     * excludeAppointmentId：正在結帳、尚未標記 paid 的這一筆本身理論上不會出現在
     * PaidTrue 查詢結果裡，這裡仍保留排除參數以防呆（例如日後改成先標記付款再算金額）。
     * 注意：這個 id 同時拿來比對 Appointment.id 與 WalkInOrder.id，兩者是不同表的自增主鍵，
     * 理論上可能撞號，但因為各自查詢已經先用 (personId, petName, paid=true) 篩過，
     * 撞號到剛好也符合條件的機率可忽略；如需絕對嚴謹可日後拆成兩個獨立參數。
     */
    private Optional<LocalDate> findLastBathDate(Long personId, String petName,
                                                  Long excludeId, LocalDate asOfDate) {
        List<LocalDate> bathDates = new ArrayList<>();

        appointmentRepository.findByUserIdAndPetNameAndPaidTrue(personId, petName).stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .filter(a -> !a.getDate().isAfter(asOfDate))
                .filter(a -> a.getSelectedItems() != null
                        && a.getSelectedItems().stream().anyMatch(this::isCatBathItem))
                .map(Appointment::getDate)
                .forEach(bathDates::add);

        walkInOrderRepository.findByMemberIdAndPetNameAndPaidTrue(personId, petName).stream()
                .filter(w -> excludeId == null || !w.getId().equals(excludeId))
                .filter(w -> w.getCreatedAt() != null && !w.getCreatedAt().toLocalDate().isAfter(asOfDate))
                .filter(w -> w.getItems() != null
                        && w.getItems().stream().anyMatch(item -> isCatBathCategory(item.getPerformanceCategory())))
                .map(w -> w.getCreatedAt().toLocalDate())
                .forEach(bathDates::add);

        return bathDates.stream().max(Comparator.naturalOrder());
    }

    /**
     * 需求 8-2：這隻貓「上一次完成洗澡服務」的日期，供名單頁顯示用
     * （跟 isRewashEligible 共用同一套查詢，只是不用排除某一筆特定紀錄，以今天為基準）。
     */
    public Optional<LocalDate> findLastBathDate(Long userId, String petName) {
        return findLastBathDate(userId, petName, null, LocalDate.now());
    }

    /**
     * 需求 8-2：全店貓咪回洗名單，供後台篩選 + 匯出。
     * withinWindowOnly：true 只列出「還在 90 天優惠期內」的貓（適合提醒趁優惠期再預約）；
     *                    false 只列出「已經超過 90 天沒回洗」的貓（適合喚回久未到店的客人）；
     *                    null 則列出全部有過洗澡紀錄的貓。
     */
    public List<CatRewashCandidateResponse> listCandidates(List<Pet> allCatPets, Boolean withinWindowOnly) {
        List<CatRewashCandidateResponse> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Pet pet : allCatPets) {
            if (pet.getOwner() == null) continue; // 理論上不會發生，防呆
            Optional<LocalDate> lastBath = findLastBathDate(pet.getOwner().getId(), pet.getName());
            if (lastBath.isEmpty()) continue; // 從未洗過的貓不列入回洗名單（沒有「回」洗的基準日）

            long daysSince = ChronoUnit.DAYS.between(lastBath.get(), today);
            boolean withinWindow = daysSince >= 0 && daysSince < REWASH_WINDOW_DAYS;

            if (withinWindowOnly != null && withinWindowOnly != withinWindow) continue;

            result.add(new CatRewashCandidateResponse(
                    pet.getId(), pet.getName(),
                    pet.getOwner().getName(), pet.getOwnerPhone(),
                    lastBath.get(), daysSince, withinWindow));
        }

        result.sort(Comparator.comparing(CatRewashCandidateResponse::getLastBathDate).reversed());
        return result;
    }

    /**
     * 需求 8-1 修正：90 天回洗優惠與會員儲值折扣「只能擇一」，不疊加。
     * 兩者都符合資格時，取對顧客較優惠（折扣後金額較低）的那一個；
     * 只符合其中一種就用那一種；都不符合就是原價。
     *
     * @param price                     這個項目的原價
     * @param rewashApplicable          這個項目是否符合回洗優惠資格（貓咪洗澡項目 + 距上次洗澡未滿90天）
     * @param memberDiscountApplicable  這個項目是否可享會員折扣（GroomingItem.discountEligible）且本次是儲值金付款
     * @param memberDiscountRate        會員折扣率（例如 0.9 代表 9 折）；不適用會員折扣時可傳 1.0，不影響結果
     */
    public DiscountResolution resolvePreferredDiscount(double price,
                                                        boolean rewashApplicable,
                                                        boolean memberDiscountApplicable,
                                                        double memberDiscountRate) {
        if (rewashApplicable && memberDiscountApplicable) {
            double rewashPrice = price * REWASH_DISCOUNT_RATE;
            double memberPrice = price * memberDiscountRate;
            return rewashPrice <= memberPrice
                    ? new DiscountResolution(rewashPrice, DiscountType.REWASH)
                    : new DiscountResolution(memberPrice, DiscountType.MEMBER);
        }
        if (rewashApplicable) {
            return new DiscountResolution(price * REWASH_DISCOUNT_RATE, DiscountType.REWASH);
        }
        if (memberDiscountApplicable) {
            return new DiscountResolution(price * memberDiscountRate, DiscountType.MEMBER);
        }
        return new DiscountResolution(price, DiscountType.NONE);
    }

    public record DiscountResolution(double price, DiscountType type) {}
}
