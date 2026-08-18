package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.MonthlyPerformance;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import com.petgrooming.pet_system.repository.MonthlyPerformanceRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
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
    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;

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
        if (source.getSplitFromRecordId() != null) {
            throw new IllegalArgumentException("這筆紀錄本身就是拆分產生的，不能再次拆分");
        }
        if (recordRepo.existsBySplitFromRecordId(source.getId())) {
            throw new IllegalArgumentException("這筆紀錄已經拆分過，不能重複拆分");
        }

        User toStaff = userRepository.findById(toStaffId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工：" + toStaffId));

        if (source.getStaff().getId().equals(toStaffId)) {
            throw new IllegalArgumentException("拆分對象不可與原負責員工相同");
        }

        // 對半平分，統一四捨五入到小數點第一位，避免多次拆分後尾數越來越長
        double half = Math.round(source.getPoints() / 2.0 * 10) / 10.0;

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
                .walkInOrderId(source.getWalkInOrderId())
                .category(source.getCategory())
                .points(half)
                .serviceDate(source.getServiceDate())
                .splitFromRecordId(source.getId())
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

    // ── 積分管理：全部員工 × 全部積分項目的統計矩陣 ─────────────────────
    // 「總計/隻數」= 該項目當月累積積分 ÷ 單次積分（換算完成幾隻/幾次）
    // 「換算積分」= 該項目當月累積積分本身
    // 排除 OTHER（不計分項目）；CHECKIN/CHECKOUT/COMPLETE 一併含在矩陣裡。
    public com.petgrooming.pet_system.dto.PerformanceMatrixResponse getMonthlyMatrix(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<PerformanceRecord> allRecords = recordRepo.findByMonth(ym.atDay(1), ym.atEndOfMonth());

        List<PerformanceCategory> categories = Arrays.stream(PerformanceCategory.values())
                .filter(c -> c != PerformanceCategory.OTHER)
                .toList();

        Map<Long, List<PerformanceRecord>> byStaff = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getStaff().getId()));

        List<com.petgrooming.pet_system.dto.StaffMatrixRow> rows = new ArrayList<>();
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
            com.petgrooming.pet_system.dto.StaffMatrixRow row = buildStaffMatrixRow(records, categories);
            rows.add(row);
            grandTotal += row.getTotalPoints();
            for (PerformanceCategory c : categories) {
                columnTotalsPoints.put(c, columnTotalsPoints.get(c) + row.getPointsByCategory().get(c));
            }
        }

        Map<PerformanceCategory, Double> columnTotalsCount = new EnumMap<>(PerformanceCategory.class);
        for (PerformanceCategory c : categories) {
            double unit = c.getDefaultPoints();
            columnTotalsCount.put(c, unit > 0 ? columnTotalsPoints.get(c) / unit : 0);
        }

        com.petgrooming.pet_system.dto.StaffMatrixRow totalsRow = com.petgrooming.pet_system.dto.StaffMatrixRow.builder()
                .staffId(null)
                .staffName("合計")
                .countByCategory(columnTotalsCount)
                .pointsByCategory(columnTotalsPoints)
                .totalPoints(grandTotal)
                .build();

        return com.petgrooming.pet_system.dto.PerformanceMatrixResponse.builder()
                .categories(categories)
                .rows(rows)
                .totalsRow(totalsRow)
                .grandTotal(grandTotal)
                .build();
    }

    // ── 需求：月報「明細」按鈕直接顯示該員工的積分項目統計（矩陣的單一列）──
    public com.petgrooming.pet_system.dto.StaffMatrixRow getStaffMonthlyBreakdown(
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
    private com.petgrooming.pet_system.dto.StaffMatrixRow buildStaffMatrixRow(
            List<PerformanceRecord> records, List<PerformanceCategory> categories) {
        Map<PerformanceCategory, Double> pointsByCategory = new EnumMap<>(PerformanceCategory.class);
        Map<PerformanceCategory, Double> countByCategory = new EnumMap<>(PerformanceCategory.class);
        double total = 0;

        for (PerformanceCategory c : categories) {
            double pts = records.stream()
                    .filter(r -> r.getCategory() == c)
                    .mapToDouble(PerformanceRecord::getPoints).sum();
            pointsByCategory.put(c, pts);
            countByCategory.put(c, c.getDefaultPoints() > 0 ? pts / c.getDefaultPoints() : 0);
            total += pts;
        }

        User staff = records.isEmpty() ? null : records.get(0).getStaff();
        return com.petgrooming.pet_system.dto.StaffMatrixRow.builder()
                .staffId(staff != null ? staff.getId() : null)
                .staffName(staff != null ? staff.getName() : null)
                .countByCategory(countByCategory)
                .pointsByCategory(pointsByCategory)
                .totalPoints(total)
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

    // ── 需求 12：算出「已經拆分過」的紀錄 id 集合（原始來源 + 拆分產生的新紀錄都算）──
    // 用來從候選清單、日報顯示中排除，這些紀錄之後只在「拆分歷史」看得到。
    private Set<Long> getSplitInvolvedRecordIds() {
        Set<Long> involved = new HashSet<>();
        for (PerformanceRecord r : recordRepo.findBySplitFromRecordIdIsNotNull()) {
            involved.add(r.getId());                 // 拆分產生的新紀錄本身
            involved.add(r.getSplitFromRecordId());   // 被拆走一半的原始紀錄
        }
        return involved;
    }

    // ── 需求 12：日報用——當日績效紀錄，排除已拆分過的（只在拆分歷史顯示）───
    public List<PerformanceRecord> getDailyRecordsForDisplay(LocalDate date) {
        Set<Long> involved = getSplitInvolvedRecordIds();
        return getDailyRecords(date).stream()
                .filter(r -> !involved.contains(r.getId()))
                .toList();
    }

    // ── 需求 12：解析一筆績效紀錄對應的寵物名稱（預約或現場單擇一查）───
    private String resolvePetName(PerformanceRecord r) {
        if (r.getAppointmentId() != null) {
            return appointmentRepository.findById(r.getAppointmentId())
                    .map(Appointment::getPetName).orElse("—");
        }
        if (r.getWalkInOrderId() != null) {
            return walkInOrderRepository.findById(r.getWalkInOrderId())
                    .map(WalkInOrder::getPetName).orElse("—");
        }
        return "—";
    }

    // ── 需求 12：待拆分積分清單（強化版，含寵物名/服務項目，支援篩選）───
    // 篩選條件皆為選填：不給日期區間預設查當日；petNameKeyword 模糊比對；staffId 篩經手人。
    public List<com.petgrooming.pet_system.dto.SplitCandidateResponse> getSplitCandidates(
            LocalDate dateFrom, LocalDate dateTo, String petNameKeyword, Long staffId) {

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now();
        LocalDate end = dateTo != null ? dateTo : LocalDate.now();

        List<PerformanceRecord> records = recordRepo.findByMonth(start, end).stream()
                .filter(r -> !r.getServiceDate().isBefore(start) && !r.getServiceDate().isAfter(end))
                .filter(r -> r.getCategory() != PerformanceCategory.CHECKIN
                          && r.getCategory() != PerformanceCategory.CHECKOUT
                          && r.getCategory() != PerformanceCategory.OTHER)
                .filter(r -> r.getPoints() != null && r.getPoints() > 0)
                .filter(r -> staffId == null || r.getStaff().getId().equals(staffId))
                .toList();

        // 需求 12：已拆分過的紀錄（不管是被拆走一半的原始紀錄，還是拆分產生的新紀錄）
        // 不再顯示在候選清單裡，只在「拆分歷史」看得到，也不能被重複拆分
        Set<Long> splitInvolved = getSplitInvolvedRecordIds();
        records = records.stream().filter(r -> !splitInvolved.contains(r.getId())).toList();

        List<com.petgrooming.pet_system.dto.SplitCandidateResponse> result = new ArrayList<>();
        for (PerformanceRecord r : records) {
            String petName = resolvePetName(r);
            if (petNameKeyword != null && !petNameKeyword.isBlank()
                    && (petName == null || !petName.contains(petNameKeyword.trim()))) {
                continue;
            }
            result.add(com.petgrooming.pet_system.dto.SplitCandidateResponse.builder()
                    .id(r.getId())
                    .serviceDate(r.getServiceDate())
                    .petName(petName)
                    .itemLabel(r.getCategory().getLabel())
                    .staffName(r.getStaff().getName())
                    .staffId(r.getStaff().getId())
                    .points(r.getPoints())
                    .halfPoints(Math.round(r.getPoints() / 2.0 * 10) / 10.0)
                    .build());
        }
        result.sort((a, b) -> b.getServiceDate().compareTo(a.getServiceDate()));
        return result;
    }

    // ── 需求 12：拆分歷史查詢（誰在什麼時候把哪隻寵物的哪個項目拆給了誰）─
    public List<com.petgrooming.pet_system.dto.SplitHistoryResponse> getSplitHistory(
            LocalDate dateFrom, LocalDate dateTo, String petNameKeyword, Long staffId) {

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().minusDays(30);
        LocalDate end = dateTo != null ? dateTo : LocalDate.now();

        List<PerformanceRecord> splitRecords = recordRepo
                .findBySplitFromRecordIdIsNotNullAndServiceDateBetweenOrderByServiceDateDesc(start, end);

        List<com.petgrooming.pet_system.dto.SplitHistoryResponse> result = new ArrayList<>();
        for (PerformanceRecord splitRec : splitRecords) {
            if (staffId != null && !splitRec.getStaff().getId().equals(staffId)
                    && !isOriginalStaffMatch(splitRec, staffId)) {
                continue;
            }
            String petName = resolvePetName(splitRec);
            if (petNameKeyword != null && !petNameKeyword.isBlank()
                    && (petName == null || !petName.contains(petNameKeyword.trim()))) {
                continue;
            }
            String fromStaffName = recordRepo.findById(splitRec.getSplitFromRecordId())
                    .map(orig -> orig.getStaff().getName())
                    .orElse("（原紀錄已不存在）");

            result.add(com.petgrooming.pet_system.dto.SplitHistoryResponse.builder()
                    .splitRecordId(splitRec.getId())
                    .serviceDate(splitRec.getServiceDate())
                    .petName(petName)
                    .itemLabel(splitRec.getCategory().getLabel())
                    .fromStaffName(fromStaffName)
                    .toStaffName(splitRec.getStaff().getName())
                    .halfPoints(splitRec.getPoints())
                    .build());
        }
        return result;
    }

    private boolean isOriginalStaffMatch(PerformanceRecord splitRec, Long staffId) {
        return recordRepo.findById(splitRec.getSplitFromRecordId())
                .map(orig -> orig.getStaff().getId().equals(staffId))
                .orElse(false);
    }
}
