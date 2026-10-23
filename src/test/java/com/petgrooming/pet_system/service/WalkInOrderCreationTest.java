package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse.ItemLine;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.GroomingItemComponent;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemComponentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 現場開單：服務項目快照、套餐副組成展開、零售商品、會員、僅限既有客戶項目、經手人積分與開單人接進積分。
 */
class WalkInOrderCreationTest extends IntegrationTestBase {

    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private RetailProductService retailProductService;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private GroomingItemComponentRepository componentRepository;
    @Autowired private PerformanceRecordRepository performanceRecordRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TestData testData;

    @Test
    void orderWithoutAnyItemIsRejected() {
        assertThatThrownBy(() -> walkInOrderService.create(request(null, "小白", List.of(), List.of()),
                testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessage("請至少加入一個美容項目或零售商品");
    }

    @Test
    void serviceItemIsSnapshottedAndCreatorGetsCheckinPoints() {
        User staff = testData.staff();
        GroomingItem bath = groomingItem(1200, 3.0, PerformanceCategory.BATH_SMALL, false, true);

        WalkInOrderResponse order = walkInOrderService.create(
                request(null, "小白", List.of(line(bath, null, null)), List.of()), staff.getUsername());

        assertThat(order.getTotalAmount()).isEqualTo(1200);
        assertThat(order.getCreatedBy()).isEqualTo(staff.getName());
        assertThat(order.getPetName()).isEqualTo("小白");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getItemName()).isEqualTo(bath.getName());
            assertThat(item.getPrice()).isEqualTo(1200);
            assertThat(item.getPoints()).isEqualTo(3.0);
            assertThat(item.isDiscountEligible()).isTrue();
            assertThat(item.isOperatorFilled()).isFalse();
        });
        assertThat(records(order)).singleElement().satisfies(r -> {
            assertThat(r.getCategory()).isEqualTo(PerformanceCategory.CHECKIN);
            assertThat(r.getPoints()).isEqualTo(PerformanceCategory.CHECKIN.getDefaultPoints());
            assertThat(r.getStaff().getId()).isEqualTo(staff.getId());
        });
    }

    @Test
    void packageComponentsAreExpandedAsZeroPriceLines() {
        GroomingItem combo = groomingItem(1500, 2.0, PerformanceCategory.BATH_SMALL, false, true);
        componentRepository.save(GroomingItemComponent.builder()
                .groomingItem(combo).performanceCategory(PerformanceCategory.TRIM).points(1.5).build());

        WalkInOrderResponse order = walkInOrderService.create(
                request(null, "小黑", List.of(line(combo, null, null)), List.of()), testData.staff().getUsername());

        assertThat(order.getTotalAmount()).isEqualTo(1500);
        assertThat(order.getItems()).extracting(ItemLine::getItemName).containsExactly(
                combo.getName() + "（" + PerformanceCategory.BATH_SMALL.getLabel() + "）",
                combo.getName() + "（" + PerformanceCategory.TRIM.getLabel() + "）");
        ItemLine component = order.getItems().get(1);
        assertThat(component.getPrice()).isZero();
        assertThat(component.getPoints()).isEqualTo(1.5);
        assertThat(component.isDiscountEligible()).isFalse();
    }

    @Test
    void retailItemsRepeatPerQuantityAndPureRetailOrderIsAllowed() {
        RetailProduct shampoo = retailProductService.create("開單測試商品-" + UUID.randomUUID().toString().substring(0, 6),
                350, 10, null, 100);

        WalkInOrderResponse order = walkInOrderService.create(
                request(null, null, List.of(), List.of(retail(shampoo, 2), retail(shampoo, 0))),
                testData.staff().getUsername());

        assertThat(order.getTotalAmount()).isEqualTo(700);
        assertThat(order.getItems()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getRetailProductId()).isEqualTo(shampoo.getId());
            assertThat(item.getItemName()).isEqualTo(shampoo.getName());
            assertThat(item.getPrice()).isEqualTo(350);
            assertThat(item.isDiscountEligible()).isFalse();
        });
    }

    @Test
    void operatorsAssignedAtCreationEarnPointsImmediately() {
        User staff = testData.staff();
        User first = testData.staff();
        User second = testData.staff();
        GroomingItem bath = groomingItem(1000, 4.0, PerformanceCategory.BATH_SMALL, false, true);
        GroomingItem trim = groomingItem(800, 3.0, PerformanceCategory.TRIM, false, true);

        WalkInOrderResponse order = walkInOrderService.create(request(null, "小花",
                List.of(line(bath, first.getId(), second.getId()), line(trim, first.getId(), null)), List.of()),
                staff.getUsername());

        assertThat(order.getItems()).allMatch(ItemLine::isOperatorFilled);
        assertThat(records(order))
                .filteredOn(r -> r.getCategory() != PerformanceCategory.CHECKIN)
                .extracting(PerformanceRecord::getPoints)
                .containsExactlyInAnyOrder(2.0, 2.0, 3.0);
    }

    @Test
    void secondOperatorWithoutFirstIsRejected() {
        GroomingItem bath = groomingItem(1000, 4.0, PerformanceCategory.BATH_SMALL, false, true);

        assertThatThrownBy(() -> walkInOrderService.create(request(null, "小花",
                List.of(line(bath, null, testData.staff().getId())), List.of()), testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void memberIsLinkedAndUnknownMemberIsRejected() {
        User member = testData.customer();
        GroomingItem bath = groomingItem(1000, 1.0, PerformanceCategory.BATH_SMALL, false, true);

        WalkInOrderResponse order = walkInOrderService.create(
                request(member.getUsername(), "小白", List.of(line(bath, null, null)), List.of()),
                testData.staff().getUsername());

        assertThat(order.getMemberUsername()).isEqualTo(member.getUsername());
        assertThatThrownBy(() -> walkInOrderService.create(
                request("nobody-" + UUID.randomUUID() + "@test.local", "小白", List.of(line(bath, null, null)), List.of()),
                testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("找不到會員");
    }

    @Test
    void unknownItemCodeIsRejected() {
        WalkInOrderCreateRequest.Item missing = new WalkInOrderCreateRequest.Item();
        missing.setItemCode("NO-SUCH-" + UUID.randomUUID());

        assertThatThrownBy(() -> walkInOrderService.create(request(null, "小白", List.of(missing), List.of()),
                testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("找不到項目代碼");
    }

    @Test
    void existingCustomerOnlyItemNeedsLinkedMemberWithHistory() {
        User member = testData.customer();
        GroomingItem membersOnly = groomingItem(600, 1.0, PerformanceCategory.BATH_SMALL, true, true);

        assertThatThrownBy(() -> walkInOrderService.create(
                request(null, "老客人", List.of(line(membersOnly, null, null)), List.of()), testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("僅限既有客戶");
        assertThatThrownBy(() -> walkInOrderService.create(
                request(member.getUsername(), "老客人", List.of(line(membersOnly, null, null)), List.of()),
                testData.staff().getUsername()))
                .isInstanceOf(PaymentException.class);

        paidAppointment(member, "老客人");
        WalkInOrderResponse order = walkInOrderService.create(
                request(member.getUsername(), "老客人", List.of(line(membersOnly, null, null)), List.of()),
                testData.staff().getUsername());
        assertThat(order.getTotalAmount()).isEqualTo(600);
    }

    private List<PerformanceRecord> records(WalkInOrderResponse order) {
        return performanceRecordRepository.findByWalkInOrderId(order.getId());
    }

    private void paidAppointment(User owner, String petName) {
        appointmentRepository.save(Appointment.builder()
                .user(owner).petName(petName).petType("DOG")
                .date(LocalDate.now().minusMonths(1)).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .totalAmount(1000).paid(true).status(AppointmentStatus.COMPLETED).build());
    }

    private GroomingItem groomingItem(int price, double points, PerformanceCategory category,
                                      boolean existingCustomersOnly, boolean discountEligible) {
        String code = "W" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code).name("開單測試項目" + code).price((double) price).points(points)
                .performanceCategory(category).requiresExistingCustomer(existingCustomersOnly)
                .discountEligible(discountEligible).build());
    }

    private static WalkInOrderCreateRequest.Item line(GroomingItem item, Long operator1, Long operator2) {
        WalkInOrderCreateRequest.Item line = new WalkInOrderCreateRequest.Item();
        line.setItemCode(item.getItemCode());
        line.setOperatorStaffId(operator1);
        line.setOperatorStaffId2(operator2);
        return line;
    }

    private static WalkInOrderCreateRequest.RetailItem retail(RetailProduct product, int quantity) {
        WalkInOrderCreateRequest.RetailItem line = new WalkInOrderCreateRequest.RetailItem();
        line.setRetailProductId(product.getId());
        line.setQuantity(quantity);
        return line;
    }

    private static WalkInOrderCreateRequest request(String member, String petName,
                                                    List<WalkInOrderCreateRequest.Item> items,
                                                    List<WalkInOrderCreateRequest.RetailItem> retail) {
        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(member);
        req.setPetName(petName);
        req.setItems(new ArrayList<>(items));
        req.setRetailItems(new ArrayList<>(retail));
        return req;
    }
}
