package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.dto.CatRewashCandidateResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse.ItemLine;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DiscountType;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 貓咪回洗名單（預約與現場單的洗澡紀錄都算、依 90 天篩選），以及現場單的多貓優惠。
 */
class CatRewashListAndMultiCatWalkInTest extends IntegrationTestBase {

    @Autowired private CatRewashDiscountService catRewashDiscountService;
    @Autowired private PetService petService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private WalkInPaymentService walkInPaymentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    @Test
    void rewashListIncludesAppointmentAndWalkInBathsAndFiltersBy90Days() {
        User owner = testData.customer();
        PetResponse recent = testData.cat(owner);
        PetResponse overdue = testData.cat(owner);
        PetResponse nailOnly = testData.cat(owner);
        PetResponse walkInBath = testData.cat(owner);
        paidAppointment(owner, recent.getName(), LocalDate.now().minusDays(30), item(1000, PerformanceCategory.BATH_CAT_S));
        paidAppointment(owner, overdue.getName(), LocalDate.now().minusDays(100), item(1000, PerformanceCategory.BATH_CAT_L));
        paidAppointment(owner, nailOnly.getName(), LocalDate.now().minusDays(10), item(200, PerformanceCategory.OTHER));
        Long order = walkIn(owner, walkInBath.getName(), item(1000, PerformanceCategory.BATH_CAT_S));
        walkInPaymentService.checkout(order, PaymentMethod.CASH, testData.staff().getUsername());

        assertThat(names(null)).contains(recent.getName(), overdue.getName(), walkInBath.getName())
                .doesNotContain(nailOnly.getName());
        assertThat(names(true)).contains(recent.getName(), walkInBath.getName()).doesNotContain(overdue.getName());
        assertThat(names(false)).contains(overdue.getName()).doesNotContain(recent.getName(), walkInBath.getName());
        assertThat(candidates(null)).filteredOn(c -> c.getPetName().equals(recent.getName()))
                .singleElement().satisfies(c -> {
                    assertThat(c.getDaysSinceLastBath()).isEqualTo(30L);
                    assertThat(c.isWithinDiscountWindow()).isTrue();
                });
    }

    @Test
    void twoReturningCatsFullGroomOnWalkInsGetMultiCat() {
        User owner = testData.customer();
        PetResponse a = returningCat(owner);
        PetResponse b = returningCat(owner);
        Long first = walkIn(owner, a.getName(), fullGroom(2000), item(300, PerformanceCategory.OTHER));
        walkIn(owner, b.getName(), fullGroom(2200));

        WalkInOrderResponse result = walkInPaymentService.checkout(first, PaymentMethod.CASH, testData.staff().getUsername());
        assertThat(result.getChargedAmount()).isEqualTo(1800 + 300);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.MULTI_CAT, DiscountType.NONE);
    }

    @Test
    void appointmentAndWalkInSameDayCountTogether() {
        User owner = testData.customer();
        PetResponse a = returningCat(owner);
        PetResponse b = returningCat(owner);
        GroomingItem groom = fullGroom(2000);
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(a.getName()).petType("CAT")
                .date(LocalDate.now()).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(groom)))
                .totalAmount(2000).paid(false).status(AppointmentStatus.IN_PROGRESS).build());
        Long order = walkIn(owner, b.getName(), fullGroom(2000));

        WalkInOrderResponse result = walkInPaymentService.checkout(order, PaymentMethod.CASH, testData.staff().getUsername());
        assertThat(result.getChargedAmount()).isEqualTo(1800);
        assertThat(result.getItems()).extracting(ItemLine::getAppliedDiscountType)
                .containsExactly(DiscountType.MULTI_CAT);
    }

    @Test
    void singleReturningCatFullGroomOnWalkInHasNoDiscount() {
        User owner = testData.customer();
        PetResponse a = returningCat(owner);
        Long order = walkIn(owner, a.getName(), fullGroom(2000));

        WalkInOrderResponse result = walkInPaymentService.checkout(order, PaymentMethod.CASH, testData.staff().getUsername());
        assertThat(result.getChargedAmount()).isEqualTo(2000);
    }

    /** 一年前有過非洗澡消費：不算首次體驗，也超過 90 天回洗期間。 */
    private PetResponse returningCat(User owner) {
        PetResponse cat = testData.cat(owner);
        paidAppointment(owner, cat.getName(), LocalDate.now().minusYears(1), item(500, PerformanceCategory.OTHER));
        return cat;
    }

    private List<String> names(Boolean withinWindowOnly) {
        return candidates(withinWindowOnly).stream().map(CatRewashCandidateResponse::getPetName).toList();
    }

    private List<CatRewashCandidateResponse> candidates(Boolean withinWindowOnly) {
        return transactionTemplate.execute(s ->
                catRewashDiscountService.listCandidates(petService.findAllByType(PetType.CAT), withinWindowOnly));
    }

    private void paidAppointment(User owner, String petName, LocalDate date, GroomingItem item) {
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("CAT")
                .date(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .selectedItems(new ArrayList<>(List.of(item)))
                .totalAmount(item.getPrice().intValue()).paid(true).status(AppointmentStatus.COMPLETED)
                .build());
    }

    private Long walkIn(User member, String petName, GroomingItem... items) {
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(member.getUsername());
        req.setPetName(petName);
        List<WalkInOrderCreateRequest.Item> lines = new ArrayList<>();
        for (GroomingItem item : items) {
            WalkInOrderCreateRequest.Item line = new WalkInOrderCreateRequest.Item();
            line.setItemCode(item.getItemCode());
            lines.add(line);
        }
        req.setItems(lines);
        req.setRetailItems(new ArrayList<>());
        Long id = walkInOrderService.create(req, testData.staff().getUsername()).getId();
        WalkInOrder order = walkInOrderRepository.findById(id).orElseThrow();
        order.setServiceEndedDone(true);
        order.setFinalCheckDone(true);
        walkInOrderRepository.save(order);
        return id;
    }

    private GroomingItem fullGroom(int price) {
        return save("大美容-測試-" + UUID.randomUUID().toString().substring(0, 6), price, PerformanceCategory.BATH_CAT_S);
    }

    private GroomingItem item(int price, PerformanceCategory category) {
        return save("回洗測試項目" + UUID.randomUUID().toString().substring(0, 6), price, category);
    }

    private GroomingItem save(String name, int price, PerformanceCategory category) {
        String code = "R" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name(name).price((double) price).points(1.0)
                .performanceCategory(category).build());
    }
}
