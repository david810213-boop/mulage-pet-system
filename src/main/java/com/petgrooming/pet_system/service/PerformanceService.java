package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.PerformanceCategory;
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

    // ── 現場開單專用：同一份積分邏輯，但對應現場單而非預約 ────────────
    @Transactional
    public PerformanceRecord addWalkInRecord(Long staffId, Long walkInOrderId,
                                             PerformanceCategory category, Double points,
                                             LocalDate serviceDate, String note) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工：" + staffId));

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

    // ── 拆分積分：從既有紀錄「對半平分」給另一位員工 ──────────────────────
    // 用於兩位員工共同完成同一項目的情境（例如各洗一半）。
    // 僅支援對半拆分（不接受任意手動輸入的小數），因為「除以 2」在浮點數運算中
    // 一定是精確運算，不會產生累積誤差；若開放任意小數輸入，長期下來可能因為
    // 二進位浮點數無法精確表示部分十進位小數，讓報表出現對不起來的尾數。
    // 會直接修正原始紀錄的積分（而非單純疊加新紀錄），確保同一筆預約的積分總和不會憑空增加。
    @Transactional
    public PerformanceRecord splitRecord(Long sourceRecordId, Long toStaffId, String note) {
        PerformanceRecord source = recordRepo.findById(sourceRecordId)
                .orElseThrow(() -> new IllegalArgumentException("找不到原始績效紀錄：" + sourceRecordId));

        if (source.getPoints() == null || source.getPoints() <= 0) {
            throw new IllegalArgumentException("原始紀錄目前積分為 0，無法拆分");
        }

        User toStaff = userRepository.findById(toStaffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工：" + toStaffId));

        if (source.getStaff().getId().equals(toStaffId)) {
            throw new IllegalArgumentException("拆分對象不可與原負責員工相同");
        }

        // 對半平分：除以 2 在浮點數運算中一定精確，不會有累積誤差
        double half = source.getPoints() / 2.0;

        // 1. 原始紀錄改為一半積分
        source.setPoints(half);
        String originalStaffName = source.getStaff().getName();
        source.setNote((source.getNote() == null ? "" : source.getNote() + "；")
                + "已對半拆分 " + half + " 分給 " + toStaff.getName());
        recordRepo.save(source);

        // 2. 新增一筆屬於拆分對象的紀錄，積分總和與原本相同（不會憑空增加）
        PerformanceRecord split = PerformanceRecord.builder()
                .staff(toStaff)
                .appointmentId(source.getAppointmentId())
                .category(source.getCategory())
                .points(half)
                .serviceDate(source.getServiceDate())
                .note((note == null || note.isBlank()
                        ? "對半拆分自 " + originalStaffName
                        : note) + "（原紀錄 #" + source.getId() + "）")
                .build();

        log.info("績效拆分：預約 #{} 的 {} 積分紀錄 #{}，對半拆分為 {} 分 / {} 分，{} ← {}",
                source.getAppointmentId(), source.getCategory().getLabel(), source.getId(),
                half, half, toStaff.getName(), originalStaffName);

        return recordRepo.save(split);
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
            throw new IllegalArgumentException("只能取消「當月」的結算，避免影響已對外發放獎金的過去月份");
        }
        LocalDate ymKey = target.atDay(1);
        List<MonthlyPerformance> rows = monthlyRepo.findByYearMonthOrderByTotalPointsDesc(ymKey);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("此月份尚未結算過，無需取消");
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
    public com.petgrooming.pet_system.dto.StaffProgressResponse getMyProgress(Long staffId, LocalDate today) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工：" + staffId));

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

        return com.petgrooming.pet_system.dto.StaffProgressResponse.builder()
                .staffName(staff.getName())
                .todayPoints(todayPoints)
                .monthMainPoints(mainPts)
                .monthReceptionPoints(receptionPts)
                .currentBonus(currentBonus)
                .nextThreshold(nextThreshold)
                .pointsToNextThreshold(pointsToNext)
                .build();
    }

    // ── 私有：依積分查獎勵金（改成查後台可編輯的 bonus_tiers 資料表）────
    private int calcBonus(int points) {
        for (BonusTier tier : bonusTierRepository.findAllByOrderByMinPointsAsc()) {
            if (points >= tier.getMinPoints() && points <= tier.getMaxPoints()) {
                return tier.getBonusAmount();
            }
        }
        return 0; // 未達最低門檻
    }

    // ── 獎金級距管理（後台可編輯）────────────────────────────────────
    public List<BonusTier> getAllBonusTiers() {
        return bonusTierRepository.findAllByOrderByMinPointsAsc();
    }

    @Transactional
    public BonusTier createBonusTier(int minPoints, int maxPoints, int bonusAmount) {
        if (minPoints > maxPoints) {
            throw new IllegalArgumentException("下限不能大於上限");
        }
        return bonusTierRepository.save(
                BonusTier.builder().minPoints(minPoints).maxPoints(maxPoints).bonusAmount(bonusAmount).build());
    }

    @Transactional
    public void updateBonusTier(Long id, int minPoints, int maxPoints, int bonusAmount) {
        if (minPoints > maxPoints) {
            throw new IllegalArgumentException("下限不能大於上限");
        }
        BonusTier tier = bonusTierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到級距 #" + id));
        tier.setMinPoints(minPoints);
        tier.setMaxPoints(maxPoints);
        tier.setBonusAmount(bonusAmount);
        bonusTierRepository.save(tier);
    }

    @Transactional
    public void deleteBonusTier(Long id) {
        bonusTierRepository.deleteById(id);
    }
}
