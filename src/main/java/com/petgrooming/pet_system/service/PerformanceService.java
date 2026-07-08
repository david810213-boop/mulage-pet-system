package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.MonthlyPerformanceRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRecordRepository recordRepo;
    private final MonthlyPerformanceRepository monthlyRepo;
    private final UserRepository userRepository;

    // ── 積分級距獎勵金對照表 ────────────────────────────────────────────
    private static final int[][] BONUS_TABLE = {
        {3001, 3350, 1200},
        {3351, 3700, 1600},
        {3701, 4000, 2200},
        {4001, 4300, 2800},
        {4301, 4600, 3600},
        {4601, 5000, 4400},
        {5001, 5300, 5600},
        {5301, 5600, 6600},
    };

    // ── 新增績效紀錄（由店家後台操作，記錄員工完成某項目的積分）──────
    @Transactional
    public PerformanceRecord addRecord(Long staffId, Long appointmentId,
                                       PerformanceCategory category, Double points,
                                       LocalDate serviceDate, String note) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工：" + staffId));

        PerformanceRecord record = PerformanceRecord.builder()
                .staff(staff)
                .appointmentId(appointmentId)
                .category(category)
                .points(points)
                .serviceDate(serviceDate)
                .note(note)
                .build();

        return recordRepo.save(record);
    }

    // ── 查詢某員工某月績效明細 ──────────────────────────────────────────
    public List<PerformanceRecord> getMonthlyRecords(Long staffId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return recordRepo.findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(
                staffId, ym.atDay(1), ym.atEndOfMonth());
    }

    // ── 查詢某預約的所有績效紀錄 ────────────────────────────────────────
    public List<PerformanceRecord> getByAppointment(Long appointmentId) {
        return recordRepo.findByAppointmentId(appointmentId);
    }

    // ── 月底結算：統計所有員工本月積分並計算獎勵金 ──────────────────
    @Transactional
    public List<MonthlyPerformance> settleMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();
        LocalDate ymKey = start;

        List<PerformanceRecord> allRecords = recordRepo.findByMonth(start, end);

        // 依員工分組
        Map<Long, List<PerformanceRecord>> byStaff = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getStaff().getId()));

        List<MonthlyPerformance> results = new ArrayList<>();

        for (Map.Entry<Long, List<PerformanceRecord>> entry : byStaff.entrySet()) {
            Long staffId = entry.getKey();
            List<PerformanceRecord> records = entry.getValue();

            // 接待積分單獨統計
            double receptionPts = records.stream()
                    .filter(r -> r.getCategory() == PerformanceCategory.CHECKIN
                              || r.getCategory() == PerformanceCategory.CHECKOUT)
                    .mapToDouble(PerformanceRecord::getPoints).sum();

            // 主要積分（不含接待、OTHER）
            double mainPts = records.stream()
                    .filter(r -> r.getCategory() != PerformanceCategory.CHECKIN
                              && r.getCategory() != PerformanceCategory.CHECKOUT
                              && r.getCategory() != PerformanceCategory.OTHER)
                    .mapToDouble(PerformanceRecord::getPoints).sum();

            int bonus = calcBonus((int) mainPts);

            // 更新或建立月績效
            User staff = records.get(0).getStaff();
            MonthlyPerformance mp = monthlyRepo
                    .findByStaffIdAndYearMonth(staffId, ymKey)
                    .orElse(MonthlyPerformance.builder().staff(staff).yearMonth(ymKey).build());

            mp.setTotalPoints(mainPts);
            mp.setReceptionPoints(receptionPts);
            mp.setBonusAmount(bonus);

            results.add(monthlyRepo.save(mp));
            log.info("結算員工 {} 的 {} 績效：主要積分={}, 接待積分={}, 獎勵金={}",
                    staff.getName(), ym, mainPts, receptionPts, bonus);
        }

        return results;
    }

    // ── 查詢某月所有員工結算結果（排行榜） ──────────────────────────
    public List<MonthlyPerformance> getMonthlyRanking(int year, int month) {
        return monthlyRepo.findByYearMonthOrderByTotalPointsDesc(
                YearMonth.of(year, month).atDay(1));
    }

    // ── 查詢某員工所有月度紀錄 ──────────────────────────────────────
    public List<MonthlyPerformance> getStaffHistory(Long staffId) {
        return monthlyRepo.findByStaffIdOrderByYearMonthDesc(staffId);
    }

    // ── 查詢某日績效（店家後台日報用）──────────────────────────────
    public List<PerformanceRecord> getDailyRecords(LocalDate date) {
        return recordRepo.findByServiceDate(date);
    }

    // ── 私有：依積分查獎勵金 ────────────────────────────────────────
    private int calcBonus(int points) {
        for (int[] range : BONUS_TABLE) {
            if (points >= range[0] && points <= range[1]) {
                return range[2];
            }
        }
        return 0; // 未達最低門檻（3001分）
    }
}
