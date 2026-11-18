package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.dto.FinancialReportResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 沒收的訂金與臨時取消扣的儲值金算營業收入：首頁今日營收、財務報表的總業績與「取消扣款收入」都要算進去。
 */
class CancellationIncomeTest extends IntegrationTestBase {

    @Autowired private DashboardService dashboardService;
    @Autowired private FinancialReportService financialReportService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TestData testData;

    @Test
    void forfeitedDepositAndLateChargeCountAsRevenueOnTheCancelDay() {
        int dashboardBefore = dashboardService.todayRevenue(LocalDate.now());
        FinancialReportResponse before = financialReportService.getFinancialReport();

        cancelled(500, null);
        cancelled(0, 660);
        cancelled(0, null); // 沒有扣款的取消不算

        FinancialReportResponse after = financialReportService.getFinancialReport();
        assertThat(dashboardService.todayRevenue(LocalDate.now())).isEqualTo(dashboardBefore + 1160);
        assertThat(after.getTodayCancellationIncome()).isEqualTo(before.getTodayCancellationIncome() + 1160);
        assertThat(after.getTodayRevenueTotal()).isEqualTo(before.getTodayRevenueTotal() + 1160);
        assertThat(after.getTodayRevenueWallet()).isEqualTo(before.getTodayRevenueWallet() + 660);
        assertThat(after.getTodayRevenueNonWallet()).isEqualTo(before.getTodayRevenueNonWallet() + 500);
        assertThat(after.getTodayOrderCount()).isEqualTo(before.getTodayOrderCount());
        assertThat(after.getDetails()).extracting(FinancialReportResponse.RevenueDetailLine::getSourceLabel)
                .contains("取消扣款");
    }

    private void cancelled(int forfeited, Integer lateCharge) {
        User owner = testData.customer();
        Appointment a = Appointment.builder()
                .user(owner).petName("取消收入" + UUID.randomUUID().toString().substring(0, 6)).petType("CAT")
                .date(LocalDate.now()).startTime(LocalTime.NOON).endTime(LocalTime.of(13, 0))
                .selectedItems(new ArrayList<>())
                .totalAmount(2200).paid(false).status(AppointmentStatus.CANCELLED)
                .cancelledAt(LocalDateTime.now()).cancelledBy("員工取消：測試")
                .build();
        a.setDepositForfeitedAmount(forfeited);
        a.setLateCancelCharge(lateCharge);
        appointmentRepository.save(a);
    }
}
