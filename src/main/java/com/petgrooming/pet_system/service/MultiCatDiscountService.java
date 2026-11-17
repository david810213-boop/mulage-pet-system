package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.discount.SpecialDiscountPolicy;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 多貓優惠：同一位會員同一天帶兩位以上貓咪做大美容（洗＋剃），每位的大美容都 9 折，不限回訪時間。
 * 跟其他優惠、會員儲值折扣一樣擇優不疊加（店家 2026-11-18 確認），見 docs/adr/0004-discount-strategy.md。
 *
 * 「大美容」以項目名稱判斷（服務項目「大美容-單層毛-單次」等，預約與開單都存名稱快照）；
 * 大美容套餐的積分分類是貓咪洗澡，所以折扣套在這位貓咪的洗澡分類項目上，也就是大美容本身。
 * 同一天的貓咪包含預約（未取消）與綁定會員的現場單。
 */
@Service
@RequiredArgsConstructor
public class MultiCatDiscountService implements SpecialDiscountPolicy {

    public static final double MULTI_CAT_RATE = 0.9;
    static final String FULL_GROOM_NAME = "大美容";
    private static final int MIN_CATS = 2;

    private static final Set<PerformanceCategory> CAT_BATH_CATEGORIES =
            EnumSet.of(PerformanceCategory.BATH_CAT_S, PerformanceCategory.BATH_CAT_L);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final PetRepository petRepository;

    @Override
    public DiscountType type() {
        return DiscountType.MULTI_CAT;
    }

    @Override
    public double rate() {
        return MULTI_CAT_RATE;
    }

    @Override
    public boolean appliesTo(PerformanceCategory category) {
        return CAT_BATH_CATEGORIES.contains(category);
    }

    @Override
    public boolean isEligible(Appointment appointment) {
        if (!"CAT".equalsIgnoreCase(appointment.getPetType()) || appointment.getUser() == null
                || !hasFullGroom(appointment)) {
            return false;
        }
        return fullGroomCatsOn(appointment.getUser().getId(), appointment.getUser().getUsername(),
                appointment.getDate()).size() >= MIN_CATS;
    }

    @Override
    public boolean isEligible(WalkInOrder order) {
        if (order.getMember() == null || !isCat(order) || !hasFullGroom(order)) {
            return false;
        }
        LocalDate day = order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : LocalDate.now();
        return fullGroomCatsOn(order.getMember().getId(), order.getMember().getUsername(), day).size() >= MIN_CATS;
    }

    /** 這位會員這一天做大美容的貓咪名字（預約與現場單合計，同名只算一次）。 */
    private Set<String> fullGroomCatsOn(Long memberId, String username, LocalDate day) {
        Set<String> cats = new HashSet<>();
        for (Appointment a : appointmentRepository.findByUserIdAndDateAndStatusNotOrderByIdAsc(
                memberId, day, AppointmentStatus.CANCELLED)) {
            if ("CAT".equalsIgnoreCase(a.getPetType()) && hasFullGroom(a)) {
                cats.add(a.getPetName());
            }
        }
        List<WalkInOrder> walkIns = walkInOrderRepository
                .findByMemberIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        memberId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        for (WalkInOrder w : walkIns) {
            if (hasFullGroom(w) && petRepository.findByOwnerUsernameAndName(username, w.getPetName())
                    .map(p -> p.getPetType() == PetType.CAT).orElse(false)) {
                cats.add(w.getPetName());
            }
        }
        return cats;
    }

    /** 到店開單後以開單項目為準；還沒開單就看顧客預約時選的項目。 */
    private boolean hasFullGroom(Appointment appointment) {
        List<AppointmentItem> checkin = appointmentItemRepository.findByAppointmentId(appointment.getId());
        if (!checkin.isEmpty()) {
            return checkin.stream().anyMatch(i -> isFullGroom(i.getItemName()));
        }
        return appointment.getSelectedItems() != null
                && appointment.getSelectedItems().stream().map(GroomingItem::getName).anyMatch(this::isFullGroom);
    }

    private boolean hasFullGroom(WalkInOrder order) {
        return order.getItems() != null
                && order.getItems().stream().map(WalkInOrderItem::getItemName).anyMatch(this::isFullGroom);
    }

    private boolean isCat(WalkInOrder order) {
        return petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .map(p -> p.getPetType() == PetType.CAT).orElse(false);
    }

    private boolean isFullGroom(String itemName) {
        return itemName != null && itemName.contains(FULL_GROOM_NAME);
    }
}
