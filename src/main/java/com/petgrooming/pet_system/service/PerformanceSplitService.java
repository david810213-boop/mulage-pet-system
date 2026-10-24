package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.SplitCandidateResponse;
import com.petgrooming.pet_system.dto.SplitHistoryResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 積分對半拆分（需求 12）：兩位員工共同完成同一項目時，把一筆績效紀錄對半分給另一位員工；待拆分清單、拆分歷史，以及日報排除已拆分的紀錄。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceSplitService {

    private final PerformanceRecordRepository recordRepo;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final PerformanceService performanceService;

    // ── 拆分積分：從既有紀錄「對半平分」給另一位員工 ──────────────────────
    // 用於兩位員工共同完成同一項目的情境（例如各洗一半）。
    // 僅支援對半拆分（不接受任意手動輸入的小數），因為「除以 2」在浮點數運算中
    // 一定是精確運算，不會產生累積誤差；若開放任意小數輸入，長期下來可能因為
    // 二進位浮點數無法精確表示部分十進位小數，讓報表出現對不起來的尾數。
    // 會直接修正原始紀錄的積分（而非單純疊加新紀錄），確保同一筆預約的積分總和不會憑空增加。
    @Transactional
    public PerformanceRecord splitRecord(Long sourceRecordId, Long toStaffId, String note) {
        PerformanceRecord source = recordRepo.findById(sourceRecordId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到原始績效紀錄：" + sourceRecordId));

        if (source.getPoints() == null || source.getPoints() <= 0) {
            throw new PerformanceException("原始紀錄目前積分為 0，無法拆分");
        }
        if (source.getSplitFromRecordId() != null) {
            throw new PerformanceException("這筆紀錄本身就是拆分產生的，不能再次拆分");
        }
        if (recordRepo.existsBySplitFromRecordId(source.getId())) {
            throw new PerformanceException("這筆紀錄已經拆分過，不能重複拆分");
        }

        User toStaff = userRepository.findById(toStaffId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工：" + toStaffId));

        if (source.getStaff().getId().equals(toStaffId)) {
            throw new PerformanceException("拆分對象不可與原負責員工相同");
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
        return performanceService.getDailyRecords(date).stream()
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
    public List<SplitCandidateResponse> getSplitCandidates(
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

        List<SplitCandidateResponse> result = new ArrayList<>();
        for (PerformanceRecord r : records) {
            String petName = resolvePetName(r);
            if (petNameKeyword != null && !petNameKeyword.isBlank()
                    && (petName == null || !petName.contains(petNameKeyword.trim()))) {
                continue;
            }
            result.add(SplitCandidateResponse.builder()
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
    public List<SplitHistoryResponse> getSplitHistory(
            LocalDate dateFrom, LocalDate dateTo, String petNameKeyword, Long staffId) {

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().minusDays(30);
        LocalDate end = dateTo != null ? dateTo : LocalDate.now();

        List<PerformanceRecord> splitRecords = recordRepo
                .findBySplitFromRecordIdIsNotNullAndServiceDateBetweenOrderByServiceDateDesc(start, end);

        List<SplitHistoryResponse> result = new ArrayList<>();
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

            result.add(SplitHistoryResponse.builder()
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

    /** 已經被拆分過的原始紀錄 id（不能再拆）。 */
    public Set<Long> getSplitSourceRecordIds() {
        return recordRepo.findBySplitFromRecordIdIsNotNull().stream()
                .map(PerformanceRecord::getSplitFromRecordId)
                .collect(Collectors.toSet());
    }
}
