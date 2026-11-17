package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 事後補第二經手人：已入帳的積分對半分給第二位；還沒入帳的，入帳時以雙人經手計算。
 */
class SecondOperatorTest extends IntegrationTestBase {

    @Autowired private SecondOperatorService secondOperatorService;
    @Autowired private AppointmentItemService appointmentItemService;
    @Autowired private PerformanceSplitService performanceSplitService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private PerformanceRecordRepository performanceRecordRepository;
    @Autowired private TestData testData;

    @Test
    void awardedPointsAreSplitWithSecondOperator() {
        User first = testData.staff();
        User second = testData.staff();
        AppointmentItem item = item(20.0);
        appointmentItemService.fillItemOperator(item.getId(), first.getId());

        secondOperatorService.addToAppointmentItem(item.getId(), second.getId());

        List<PerformanceRecord> records = recordsFor(item);
        assertThat(records).extracting(PerformanceRecord::getPoints).containsExactlyInAnyOrder(10.0, 10.0);
        assertThat(records).extracting(r -> r.getStaff().getId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(appointmentItemRepository.findById(item.getId()).orElseThrow().getOperatorStaff2().getId())
                .isEqualTo(second.getId());
    }

    @Test
    void notYetAwardedItemIsAwardedAsDualLater() {
        User first = testData.staff();
        User second = testData.staff();
        AppointmentItem item = item(20.0);
        item.setOperatorStaff(first);
        appointmentItemRepository.save(item);

        secondOperatorService.addToAppointmentItem(item.getId(), second.getId());
        assertThat(recordsFor(item)).isEmpty();

        appointmentItemService.awardPendingItemPointsForAppointment(item.getAppointment().getId());
        assertThat(recordsFor(item)).extracting(PerformanceRecord::getPoints).containsExactly(10.0, 10.0);
    }

    @Test
    void cannotAddSecondAfterSplittingToColleague() {
        User first = testData.staff();
        AppointmentItem item = item(20.0);
        appointmentItemService.fillItemOperator(item.getId(), first.getId());
        performanceSplitService.splitRecord(recordsFor(item).get(0).getId(), testData.staff().getId(), null);

        assertThatThrownBy(() -> secondOperatorService.addToAppointmentItem(item.getId(), testData.staff().getId()))
                .isInstanceOf(PerformanceException.class)
                .hasMessageContaining("拆給同事");
    }

    @Test
    void rejectsSameStaffAndSecondTime() {
        User first = testData.staff();
        AppointmentItem item = item(20.0);
        appointmentItemService.fillItemOperator(item.getId(), first.getId());

        assertThatThrownBy(() -> secondOperatorService.addToAppointmentItem(item.getId(), first.getId()))
                .isInstanceOf(PerformanceException.class);

        secondOperatorService.addToAppointmentItem(item.getId(), testData.staff().getId());
        assertThatThrownBy(() -> secondOperatorService.addToAppointmentItem(item.getId(), testData.staff().getId()))
                .isInstanceOf(PerformanceException.class)
                .hasMessageContaining("已經有第二經手人");
        assertThat(recordsFor(item)).hasSize(2);
    }

    @Test
    void candidatesListItemsWithOnlyOneOperator() {
        AppointmentItem single = item(20.0);
        appointmentItemService.fillItemOperator(single.getId(), testData.staff().getId());
        AppointmentItem dual = item(20.0);
        appointmentItemService.fillItemOperator(dual.getId(), testData.staff().getId(), testData.staff().getId());

        assertThat(secondOperatorService.candidates(LocalDate.now()))
                .extracting(c -> c.itemId())
                .contains(single.getId())
                .doesNotContain(dual.getId());
    }

    private AppointmentItem item(double points) {
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .user(testData.customer())
                .petName("毛孩")
                .petType("DOG")
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .totalAmount(1000)
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .build());
        return appointmentItemRepository.save(AppointmentItem.builder()
                .appointment(appointment)
                .itemName("剪毛")
                .price(1000)
                .points(points)
                .performanceCategory(PerformanceCategory.BATH_SMALL)
                .build());
    }

    private List<PerformanceRecord> recordsFor(AppointmentItem item) {
        return performanceRecordRepository.findByAppointmentId(item.getAppointment().getId());
    }
}
