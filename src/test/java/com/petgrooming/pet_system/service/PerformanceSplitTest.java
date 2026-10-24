package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.SplitCandidateResponse;
import com.petgrooming.pet_system.dto.SplitHistoryResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 積分對半拆分（需求 12）：待拆分清單、拆分、日報排除、拆分歷史與防呆；以及獎金級距的後台設定。
 */
class PerformanceSplitTest extends IntegrationTestBase {

    @Autowired private PerformanceService performanceService;
    @Autowired private BonusTierService bonusTierService;
    @Autowired private PerformanceSplitService performanceSplitService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TestData testData;

    @Test
    void splitsPointsInHalfAndMovesTheRecordToHistory() {
        User groomer = testData.staff();
        User helper = testData.staff();
        LocalDate date = TestData.uniqueFutureDate();
        String petName = "拆分毛孩" + UUID.randomUUID().toString().substring(0, 6);
        Appointment appointment = appointment(petName, date);
        PerformanceRecord bath = performanceService.addRecord(groomer.getId(), appointment.getId(),
                PerformanceCategory.BATH_SMALL, 3.0, date, "洗澡");
        PerformanceRecord checkin = performanceService.addRecord(groomer.getId(), appointment.getId(),
                PerformanceCategory.CHECKIN, 1.0, date, "接待");

        List<SplitCandidateResponse> candidates = candidates(date, petName, null);
        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(bath.getId());
            assertThat(c.getPetName()).isEqualTo(petName);
            assertThat(c.getItemLabel()).isEqualTo(PerformanceCategory.BATH_SMALL.getLabel());
            assertThat(c.getStaffName()).isEqualTo(groomer.getName());
            assertThat(c.getPoints()).isEqualTo(3.0);
            assertThat(c.getHalfPoints()).isEqualTo(1.5);
        });
        assertThat(candidates(date, petName, helper.getId())).isEmpty();

        PerformanceRecord split = performanceSplitService.splitRecord(bath.getId(), helper.getId(), null);

        assertThat(split.getPoints()).isEqualTo(1.5);
        assertThat(split.getStaff().getId()).isEqualTo(helper.getId());
        assertThat(split.getSplitFromRecordId()).isEqualTo(bath.getId());
        assertThat(split.getNote()).isEqualTo("對半拆分自 " + groomer.getName() + "（原紀錄 #" + bath.getId() + "）");
        PerformanceRecord source = performanceService.findRecord(bath.getId()).orElseThrow();
        assertThat(source.getPoints()).isEqualTo(1.5);
        assertThat(source.getNote()).isEqualTo("洗澡；已對半拆分 1.5 分給 " + helper.getName());

        assertThat(candidates(date, petName, null)).isEmpty();
        assertThat(performanceSplitService.getSplitSourceRecordIds()).contains(bath.getId());
        List<PerformanceRecord> daily = transactionTemplate.execute(s -> performanceSplitService.getDailyRecordsForDisplay(date));
        assertThat(daily).extracting(PerformanceRecord::getId)
                .contains(checkin.getId()).doesNotContain(bath.getId(), split.getId());

        List<SplitHistoryResponse> history = history(date, petName, null);
        assertThat(history).singleElement().satisfies(h -> {
            assertThat(h.getSplitRecordId()).isEqualTo(split.getId());
            assertThat(h.getFromStaffName()).isEqualTo(groomer.getName());
            assertThat(h.getToStaffName()).isEqualTo(helper.getName());
            assertThat(h.getHalfPoints()).isEqualTo(1.5);
        });
        assertThat(history(date, petName, groomer.getId())).hasSize(1); // 原負責員工也查得到
        assertThat(history(date, petName, testData.staff().getId())).isEmpty();
    }

    @Test
    void cannotSplitTwiceOrToTheSameStaff() {
        User groomer = testData.staff();
        User helper = testData.staff();
        LocalDate date = TestData.uniqueFutureDate();
        PerformanceRecord record = performanceService.addRecord(groomer.getId(), appointment("防呆毛孩", date).getId(),
                PerformanceCategory.TRIM, 2.0, date, null);

        assertThatThrownBy(() -> performanceSplitService.splitRecord(record.getId(), groomer.getId(), null))
                .isInstanceOf(PerformanceException.class).hasMessageContaining("不可與原負責員工相同");

        PerformanceRecord split = performanceSplitService.splitRecord(record.getId(), helper.getId(), "一起剪");
        assertThat(split.getNote()).isEqualTo("一起剪（原紀錄 #" + record.getId() + "）");
        assertThat(performanceService.findRecord(record.getId()).orElseThrow().getNote())
                .isEqualTo("已對半拆分 1.0 分給 " + helper.getName());

        assertThatThrownBy(() -> performanceSplitService.splitRecord(record.getId(), testData.staff().getId(), null))
                .isInstanceOf(PerformanceException.class).hasMessageContaining("已經拆分過");
        assertThatThrownBy(() -> performanceSplitService.splitRecord(split.getId(), groomer.getId(), null))
                .isInstanceOf(PerformanceException.class).hasMessageContaining("本身就是拆分產生的");
    }

    @Test
    void bonusTiersCanBeManaged() {
        assertThatThrownBy(() -> bonusTierService.createBonusTier(900_010, 900_000, 100))
                .isInstanceOf(PerformanceException.class).hasMessage("下限不能大於上限");

        BonusTier tier = bonusTierService.createBonusTier(900_000, 900_010, 100);
        assertThat(bonusTierService.getAllBonusTiers()).extracting(BonusTier::getId).contains(tier.getId());

        bonusTierService.updateBonusTier(tier.getId(), 900_000, 900_020, 200);
        assertThat(bonusTierService.getAllBonusTiers()).filteredOn(t -> t.getId().equals(tier.getId()))
                .singleElement().satisfies(t -> {
                    assertThat(t.getMaxPoints()).isEqualTo(900_020);
                    assertThat(t.getBonusAmount()).isEqualTo(200);
                });
        assertThatThrownBy(() -> bonusTierService.updateBonusTier(tier.getId(), 5, 1, 0))
                .isInstanceOf(PerformanceException.class);

        bonusTierService.deleteBonusTier(tier.getId());
        assertThat(bonusTierService.getAllBonusTiers()).extracting(BonusTier::getId).doesNotContain(tier.getId());
    }

    private List<SplitHistoryResponse> history(LocalDate date, String petName, Long staffId) {
        return transactionTemplate.execute(s -> performanceSplitService.getSplitHistory(date, date, petName, staffId));
    }

    private List<SplitCandidateResponse> candidates(LocalDate date, String petName, Long staffId) {
        return transactionTemplate.execute(s -> performanceSplitService.getSplitCandidates(date, date, petName, staffId));
    }

    private Appointment appointment(String petName, LocalDate date) {
        return appointmentRepository.save(Appointment.builder()
                .user(testData.customer()).petName(petName).petType("DOG")
                .date(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                .totalAmount(1000).paid(false).status(AppointmentStatus.IN_PROGRESS)
                .build());
    }
}
