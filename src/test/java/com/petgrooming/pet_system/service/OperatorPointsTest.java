package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 經手人與績效積分：每個項目可選一或兩位經手人，兩位時積分各半；不得重複計分。
 */
class OperatorPointsTest extends IntegrationTestBase {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentItemRepository appointmentItemRepository;

    @Autowired
    private PerformanceRecordRepository performanceRecordRepository;

    @Autowired
    private TestData testData;

    @Test
    void singleOperatorReceivesFullPoints() {
        User staff = testData.staff();
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);

        appointmentService.fillItemOperator(item.getId(), staff.getId());

        List<PerformanceRecord> records = recordsFor(item);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStaff().getId()).isEqualTo(staff.getId());
        assertThat(records.get(0).getPoints()).isEqualTo(3.0);
    }

    @Test
    void twoOperatorsSplitPointsEvenly() {
        User first = testData.staff();
        User second = testData.staff();
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);

        appointmentService.fillItemOperator(item.getId(), first.getId(), second.getId());

        assertThat(recordsFor(item))
                .extracting(PerformanceRecord::getPoints)
                .containsExactly(1.5, 1.5);
    }

    @Test
    void pointsAreNotAwardedTwice() {
        User staff = testData.staff();
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);
        appointmentService.fillItemOperator(item.getId(), staff.getId());

        appointmentService.awardPendingItemPointsForAppointment(item.getAppointment().getId());

        assertThat(recordsFor(item)).hasSize(1);
    }

    @Test
    void operatorCannotBeFilledTwice() {
        User staff = testData.staff();
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);
        appointmentService.fillItemOperator(item.getId(), staff.getId());

        assertThatThrownBy(() -> appointmentService.fillItemOperator(item.getId(), testData.staff().getId()))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void sameStaffCannotBeBothOperators() {
        User staff = testData.staff();
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);

        assertThatThrownBy(() -> appointmentService.fillItemOperator(item.getId(), staff.getId(), staff.getId()))
                .isInstanceOf(AppointmentException.class);
        assertThat(recordsFor(item)).isEmpty();
    }

    @Test
    void secondOperatorRequiresFirst() {
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);

        assertThatThrownBy(() -> appointmentService.fillItemOperator(item.getId(), null, testData.staff().getId()))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void customerCannotBeAnOperator() {
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.BATH_SMALL);

        assertThatThrownBy(() -> appointmentService.fillItemOperator(item.getId(), testData.customer().getId()))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("員工");
    }

    @Test
    void itemsInOtherCategoryEarnNoPoints() {
        AppointmentItem item = pendingItem(3.0, PerformanceCategory.OTHER);

        appointmentService.fillItemOperator(item.getId(), testData.staff().getId());

        assertThat(recordsFor(item)).isEmpty();
    }

    private AppointmentItem pendingItem(double points, PerformanceCategory category) {
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
                .itemName("測試項目")
                .price(1000)
                .points(points)
                .performanceCategory(category)
                .build());
    }

    private List<PerformanceRecord> recordsFor(AppointmentItem item) {
        return performanceRecordRepository.findByAppointmentId(item.getAppointment().getId());
    }
}
