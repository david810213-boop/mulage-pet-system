package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PerformanceMatrixResponse;
import com.petgrooming.pet_system.dto.StaffMatrixRow;
import com.petgrooming.pet_system.dto.StaffProgressResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import com.petgrooming.pet_system.repository.MonthlyPerformanceRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final BonusTierRepository bonusTierRepository;

    // ── 新增績效紀錄（由店家後台操作，記錄員工完成某項目的積分）──────
    @Transactional
    public PerformanceRecord addRecord(Long staffId, Long appointmentId,
                                       PerformanceCategory category, Double points,
                                       LocalDate serviceDate, String note) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + staffId));

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

    // ── 現場開單專用：同一份積分邏輯，但對應現場單而非預約 ────────────
    @Transactional
    public PerformanceRecord addWalkInRecord(Long staffId, Long walkInOrderId,
                                             PerformanceCategory category, Double points,
                                             LocalDate serviceDate, String note) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + staffId));

        PerformanceRecord record = PerformanceRecord.builder()
                .staff(staff)
                .walkInOrderId(walkInOrderId)
                .category(category)
                .points(points)
                .serviceDate(serviceDate)
                .note(note)
                .build();

        return recordRepo.save(record);
    }

    // ── 雙人經手 ──────────────────────
    // 兩位經手人各拿一半（四捨五入到小數點第一位，跟「拆給同事」同一套算法）。
    // 第二筆掛 splitFromRecordId＝第一筆，沿用拆分的防呆：兩筆都不能再拆、
    // 次數統計也會各算半次。appointmentId / walkInOrderId 二擇一。
    @Transactional
    public void addDualRecords(Long staffId1, Long staffId2, Long appointmentId, Long walkInOrderId,
                               PerformanceCategory category, Double points,
                               LocalDate serviceDate, String note) {
        User s1 = userRepository.findById(staffId1)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + staffId1));
        User s2 = userRepository.findById(staffId2)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + staffId2));
        double half = Math.round((points == null ? 0 : points) / 2.0 * 10) / 10.0;

        PerformanceRecord first = recordRepo.save(PerformanceRecord.builder()
                .staff(s1)
                .appointmentId(appointmentId)
                .walkInOrderId(walkInOrderId)
                .category(category)
                .points(half)
                .serviceDate(serviceDate)
                .note(note + "（雙人經手，與 " + s2.getName() + " 各半）")
                .build());
        recordRepo.save(PerformanceRecord.builder()
                .staff(s2)
                .appointmentId(appointmentId)
                .walkInOrderId(walkInOrderId)
                .category(category)
                .points(half)
                .serviceDate(serviceDate)
                .splitFromRecordId(first.getId())
                .note(note + "（雙人經手，與 " + s1.getName() + " 各半）")
                .build());
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

    // ── 月底結算：統計所有員工本月積分並計算獎勵金，寫入結算快照 ──────────
    @Transactional
    public List<MonthlyPerformance> settleMonth(int year, int month) {
        LocalDate ymKey = YearMonth.of(year, month).atDay(1);
        List<MonthlyPerformance> computed = computeMonthSummaries(year, month);

        List<MonthlyPerformance> results = new ArrayList<>();
        for (MonthlyPerformance c : computed) {
            MonthlyPerformance mp = monthlyRepo
                    .findByStaffIdAndYearMonth(c.getStaff().getId(), ymKey)
                    .orElse(MonthlyPerformance.builder().staff(c.getStaff()).yearMonth(ymKey).build());

            mp.setTotalPoints(c.getTotalPoints());
            mp.setReceptionPoints(c.getReceptionPoints());
            mp.setBonusAmount(c.getBonusAmount());
            mp.setIsSettled(true);
            mp.setSettledAt(LocalDateTime.now());

            results.add(monthlyRepo.save(mp));
            log.info("結算員工 {} 的 {} 績效：主要積分={}, 接待積分={}, 獎勵金={}",
                    c.getStaff().getName(), YearMonth.of(year, month), c.getTotalPoints(), c.getReceptionPoints(), c.getBonusAmount());
        }
        return results;
    }

    // ── 取消結算：僅限「當月」，避免不小心把過去已對外發放獎金的月份也取消掉 ──
    // 取消後該月份的結算快照會被刪除，回到「即時預覽、尚未結算」狀態；
    // 資料本身（PerformanceRecord）完全不受影響，需要的話隨時可以重新執行結算復原。
    @Transactional
    public void cancelSettlement(int year, int month) {
        YearMonth target = YearMonth.of(year, month);
        if (!target.equals(YearMonth.now())) {
            throw new PerformanceException("只能取消「當月」的結算，避免影響已對外發放獎金的過去月份");
        }
        LocalDate ymKey = target.atDay(1);
        List<MonthlyPerformance> rows = monthlyRepo.findByYearMonthOrderByTotalPointsDesc(ymKey);
        if (rows.isEmpty()) {
            throw new PerformanceException("此月份尚未結算過，無需取消");
        }
        monthlyRepo.deleteAll(rows);
        log.info("已取消 {} 的結算，共 {} 筆", target, rows.size());
    }

    // ── 即時月度排行（給月報頁面用）：不需要先結算，每天都會依最新積分即時更新 ──
    // 若該月份已經正式結算過，obtainSettled=true 且獎勵金取結算當下鎖定的數字；
    // 尚未結算則即時計算預覽值，obtainSettled=false。
    public List<MonthlyPerformance> getLiveMonthlyRanking(int year, int month) {
        List<MonthlyPerformance> live = computeMonthSummaries(year, month);

        LocalDate ymKey = YearMonth.of(year, month).atDay(1);
        Map<Long, MonthlyPerformance> settledByStaffId = monthlyRepo
                .findByYearMonthOrderByTotalPointsDesc(ymKey).stream()
                .collect(Collectors.toMap(mp -> mp.getStaff().getId(), mp -> mp));

        for (MonthlyPerformance mp : live) {
            MonthlyPerformance settled = settledByStaffId.get(mp.getStaff().getId());
            if (settled != null) {
                mp.setIsSettled(true);
                mp.setSettledAt(settled.getSettledAt());
            }
        }

        live.sort((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()));
        return live;
    }

    // ── 共用計算：依 PerformanceRecord 即時統計每位員工本月主要積分/接待積分/獎勵金 ──
    // 回傳的物件是「暫存、未寫入資料庫」的 MonthlyPerformance（id 為 null），
    // 只用來組顯示資料或給 settleMonth() 拿去正式寫入，不能直接拿去 save。
    private List<MonthlyPerformance> computeMonthSummaries(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();

        List<PerformanceRecord> allRecords = recordRepo.findByMonth(start, end);

        Map<Long, List<PerformanceRecord>> byStaff = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getStaff().getId()));

        List<MonthlyPerformance> results = new ArrayList<>();
        for (Map.Entry<Long, List<PerformanceRecord>> entry : byStaff.entrySet()) {
            List<PerformanceRecord> records = entry.getValue();

            double receptionPts = records.stream()
                    .filter(r -> r.getCategory() == PerformanceCategory.CHECKIN
                              || r.getCategory() == PerformanceCategory.CHECKOUT)
                    .mapToDouble(PerformanceRecord::getPoints).sum();

            double mainPts = records.stream()
                    .filter(r -> r.getCategory() != PerformanceCategory.CHECKIN
                              && r.getCategory() != PerformanceCategory.CHECKOUT
                              && r.getCategory() != PerformanceCategory.OTHER)
                    .mapToDouble(PerformanceRecord::getPoints).sum();

            User staff = records.get(0).getStaff();
            results.add(MonthlyPerformance.builder()
                    .staff(staff)
                    .yearMonth(start)
                    .totalPoints(mainPts)
                    .receptionPoints(receptionPts)
                    .bonusAmount(calcBonus((int) mainPts))
                    .isSettled(false)
                    .build());
        }
        return results;
    }

    // ── 查詢某員工所有月度紀錄 ──────────────────────────────────────
    public List<MonthlyPerformance> getStaffHistory(Long staffId) {
        return monthlyRepo.findByStaffIdOrderByYearMonthDesc(staffId);
    }

    // ── 查詢某日績效（店家後台日報用）──────────────────────────────
    public List<PerformanceRecord> getDailyRecords(LocalDate date) {
        return recordRepo.findByServiceDate(date);
    }

    // ── 我的績效：員工查詢自己今日/當月累積積分，以及距離下一級距還差幾分 ──
    public StaffProgressResponse getMyProgress(Long staffId, LocalDate today) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + staffId));

        double todayPoints = recordRepo.findByServiceDate(today).stream()
                .filter(r -> r.getStaff().getId().equals(staffId))
                .mapToDouble(PerformanceRecord::getPoints).sum();

        YearMonth ym = YearMonth.from(today);
        List<PerformanceRecord> monthRecords = recordRepo
                .findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(staffId, ym.atDay(1), ym.atEndOfMonth());

        double receptionPts = monthRecords.stream()
                .filter(r -> r.getCategory() == PerformanceCategory.CHECKIN
                          || r.getCategory() == PerformanceCategory.CHECKOUT)
                .mapToDouble(PerformanceRecord::getPoints).sum();

        double mainPts = monthRecords.stream()
                .filter(r -> r.getCategory() != PerformanceCategory.CHECKIN
                          && r.getCategory() != PerformanceCategory.CHECKOUT
                          && r.getCategory() != PerformanceCategory.OTHER)
                .mapToDouble(PerformanceRecord::getPoints).sum();

        int currentBonus = calcBonus((int) mainPts);

        Integer nextThreshold = null;
        Double pointsToNext = null;
        for (BonusTier tier : bonusTierRepository.findAllByOrderByMinPointsAsc()) {
            if (mainPts < tier.getMinPoints()) {
                nextThreshold = tier.getMinPoints();
                pointsToNext = tier.getMinPoints() - mainPts;
                break;
            }
        }

        return StaffProgressResponse.builder()
                .staffName(staff.getName())
                .todayPoints(todayPoints)
                .monthMainPoints(mainPts)
                .monthReceptionPoints(receptionPts)
                .currentBonus(currentBonus)
                .nextThreshold(nextThreshold)
                .pointsToNextThreshold(pointsToNext)
                .build();
    }

    // ── 積分管理：全部員工 × 全部積分項目的統計矩陣 ─────────────────────
    // 「總計/隻數」= 該項目當月累積積分 ÷ 單次積分（換算完成幾隻/幾次）
    // 「換算積分」= 該項目當月累積積分本身
    // 排除 OTHER（不計分項目）；CHECKIN/CHECKOUT/COMPLETE 一併含在矩陣裡。
    public PerformanceMatrixResponse getMonthlyMatrix(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<PerformanceRecord> allRecords = recordRepo.findByMonth(ym.atDay(1), ym.atEndOfMonth());

        List<PerformanceCategory> categories = Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER)
                .toList();

        Map<Long, List<PerformanceRecord>> byStaff = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getStaff().getId()));

        List<StaffMatrixRow> rows = new ArrayList<>();
        Map<PerformanceCategory, Double> columnTotalsPoints = new EnumMap<>(PerformanceCategory.class);
        for (PerformanceCategory c : categories) columnTotalsPoints.put(c, 0.0);
        double grandTotal = 0;

        // 依姓名排序，畫面/匯出順序穩定
        List<Map.Entry<Long, List<PerformanceRecord>>> sortedEntries = byStaff.entrySet().stream()
                .sorted((a, b) -> a.getValue().get(0).getStaff().getName()
                        .compareTo(b.getValue().get(0).getStaff().getName()))
                .toList();

        for (Map.Entry<Long, List<PerformanceRecord>> entry : sortedEntries) {
            List<PerformanceRecord> records = entry.getValue();
            StaffMatrixRow row = buildStaffMatrixRow(records, categories);
            rows.add(row);
            grandTotal += row.getTotalPoints();
            for (PerformanceCategory c : categories) {
                columnTotalsPoints.put(c, columnTotalsPoints.get(c) + row.getPointsByCategory().get(c));
            }
        }

        Map<PerformanceCategory, Double> columnTotalsCount = new EnumMap<>(PerformanceCategory.class);
        for (PerformanceCategory c : categories) {
            double unit = c.getDefaultPoints();
            if (unit > 0) {
                columnTotalsCount.put(c, columnTotalsPoints.get(c) / unit);
            } else {
                // 不計分項目（完成）的合計次數＝每位員工次數相加
                columnTotalsCount.put(c, rows.stream()
                        .mapToDouble(r -> r.getCountByCategory().getOrDefault(c, 0.0)).sum());
            }
        }

        StaffMatrixRow totalsRow = StaffMatrixRow.builder()
                .staffId(null)
                .staffName("合計")
                .countByCategory(columnTotalsCount)
                .pointsByCategory(columnTotalsPoints)
                .totalPoints(grandTotal)
                .build();

        return PerformanceMatrixResponse.builder()
                .categories(categories)
                .rows(rows)
                .totalsRow(totalsRow)
                .grandTotal(grandTotal)
                .build();
    }

    // ── 月報「明細」按鈕直接顯示該員工的積分項目統計（矩陣的單一列）──
    public StaffMatrixRow getStaffMonthlyBreakdown(
            Long staffId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<PerformanceCategory> categories = Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER)
                .toList();
        List<PerformanceRecord> records = recordRepo
                .findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(staffId, ym.atDay(1), ym.atEndOfMonth());
        return buildStaffMatrixRow(records, categories);
    }

    // ── 私有：把一位員工當月的績效紀錄，依項目分類彙整成矩陣的一列 ─────
    private StaffMatrixRow buildStaffMatrixRow(
            List<PerformanceRecord> records, List<PerformanceCategory> categories) {
        Map<PerformanceCategory, Double> pointsByCategory = new EnumMap<>(PerformanceCategory.class);
        Map<PerformanceCategory, Double> countByCategory = new EnumMap<>(PerformanceCategory.class);
        double total = 0;

        for (PerformanceCategory c : categories) {
            double pts = records.stream()
                    .filter(r -> r.getCategory() == c)
                    .mapToDouble(PerformanceRecord::getPoints).sum();
            pointsByCategory.put(c, pts);
            // 有分數的項目：次數＝積分 ÷ 每次分數（拆給同事的算半次）；
            // 不計分的項目（例如「完成」）：直接數筆數，拆分產生的子紀錄不重複計算
            countByCategory.put(c, c.getDefaultPoints() > 0
                    ? pts / c.getDefaultPoints()
                    : records.stream().filter(r -> r.getCategory() == c && r.getSplitFromRecordId() == null).count());
            total += pts;
        }

        User staff = records.isEmpty() ? null : records.get(0).getStaff();
        return StaffMatrixRow.builder()
                .staffId(staff != null ? staff.getId() : null)
                .staffName(staff != null ? staff.getName() : null)
                .countByCategory(countByCategory)
                .pointsByCategory(pointsByCategory)
                .totalPoints(total)
                .build();
    }

    // ── 私有：依積分查獎勵金（後台可編輯的 bonus_tiers 資料表）────
    private int calcBonus(int points) {
        for (BonusTier tier : bonusTierRepository.findAllByOrderByMinPointsAsc()) {
            if (points >= tier.getMinPoints() && points <= tier.getMaxPoints()) {
                return tier.getBonusAmount();
            }
        }
        return 0; // 未達最低門檻
    }

    public List<PerformanceRecord> getRecordsBetween(Long staffId, LocalDate from, LocalDate to) {
        return recordRepo.findByStaffIdAndServiceDateBetweenOrderByServiceDateDesc(staffId, from, to);
    }

    public Optional<PerformanceRecord> findRecord(Long recordId) {
        return recordRepo.findById(recordId);
    }
}
