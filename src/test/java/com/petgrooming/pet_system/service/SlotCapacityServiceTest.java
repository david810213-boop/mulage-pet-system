package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotCapacityServiceTest extends IntegrationTestBase {

    private static final LocalTime TEN_AM = LocalTime.of(10, 0);

    @Autowired
    private SlotCapacityService slotCapacityService;

    @Test
    void reserveCountsUpToCapacityThenRejects() {
        LocalDate date = TestData.uniqueFutureDate();
        slotCapacityService.setCapacity(date, TEN_AM, 2);

        slotCapacityService.reserve(date, TEN_AM);
        slotCapacityService.reserve(date, TEN_AM);

        assertThatThrownBy(() -> slotCapacityService.reserve(date, TEN_AM))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("額滿");
        assertThat(slotCapacityService.bookedCount(date, TEN_AM)).isEqualTo(2);
    }

    @Test
    void reserveWithoutSlotRowIsRejected() {
        LocalDate date = TestData.uniqueFutureDate();

        assertThatThrownBy(() -> slotCapacityService.reserve(date, TEN_AM))
                .isInstanceOf(AppointmentException.class);
    }

    @Test
    void releaseFreesSeatButNeverGoesNegative() {
        LocalDate date = TestData.uniqueFutureDate();
        slotCapacityService.setCapacity(date, TEN_AM, 1);
        slotCapacityService.reserve(date, TEN_AM);

        slotCapacityService.release(date, TEN_AM);
        slotCapacityService.release(date, TEN_AM);

        assertThat(slotCapacityService.bookedCount(date, TEN_AM)).isZero();
    }

    @Test
    void concurrentReservationsNeverExceedCapacity() throws Exception {
        LocalDate date = TestData.uniqueFutureDate();
        int capacity = 3;
        slotCapacityService.setCapacity(date, TEN_AM, capacity);

        Concurrently.Result result = Concurrently.run(10, () -> slotCapacityService.reserve(date, TEN_AM));

        assertThat(result.succeeded()).isEqualTo(capacity);
        assertThat(result.failures()).allMatch(AppointmentException.class::isInstance);
        assertThat(slotCapacityService.bookedCount(date, TEN_AM)).isEqualTo(capacity);
    }
}
