package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.ClosedDate;
import com.petgrooming.pet_system.model.WeeklyClosureSetting;
import com.petgrooming.pet_system.repository.ClosedDateRepository;
import com.petgrooming.pet_system.repository.WeeklyClosureSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求 16：公休日設定。
 * 兩套機制並存，只要符合任一條件就是公休日：
 *   1. ClosedDate：單一天公休（國定假日、臨時店休）
 *   2. WeeklyClosureSetting：每週固定公休星期（例如每週四、五）
 */
@Service
@RequiredArgsConstructor
public class ClosedDateService {

    private final ClosedDateRepository closedDateRepository;
    private final WeeklyClosureSettingRepository weeklyClosureSettingRepository;

    public boolean isClosed(LocalDate date) {
        if (getWeeklyClosureSetting().isClosedOn(date.getDayOfWeek())) return true;
        return closedDateRepository.existsByDate(date);
    }

    @Transactional
    public void setClosed(LocalDate date, String reason) {
        if (closedDateRepository.existsByDate(date)) return; // 已是公休日，忽略重複設定
        closedDateRepository.save(ClosedDate.builder()
                .date(date)
                .reason(reason == null || reason.isBlank() ? null : reason.trim())
                .build());
    }

    @Transactional
    public void removeClosed(LocalDate date) {
        closedDateRepository.deleteByDate(date);
    }

    // 後台顯示用：今天以後（含）的公休日清單
    public List<ClosedDate> listUpcoming() {
        return closedDateRepository.findByDateGreaterThanEqualOrderByDateAsc(LocalDate.now());
    }

    // ── 固定公休星期 ──────────────────────────────────────────────────
    public WeeklyClosureSetting getWeeklyClosureSetting() {
        return weeklyClosureSettingRepository.findAll().stream().findFirst()
                .orElseGet(() -> weeklyClosureSettingRepository.save(WeeklyClosureSetting.builder().build()));
    }

    private boolean isWeeklyClosed(DayOfWeek dayOfWeek) {
        return getWeeklyClosureSetting().isClosedOn(dayOfWeek);
    }

    @Transactional
    public void updateWeeklyClosureSetting(boolean mon, boolean tue, boolean wed, boolean thu,
                                           boolean fri, boolean sat, boolean sun) {
        WeeklyClosureSetting setting = getWeeklyClosureSetting();
        setting.setClosedMonday(mon);
        setting.setClosedTuesday(tue);
        setting.setClosedWednesday(wed);
        setting.setClosedThursday(thu);
        setting.setClosedFriday(fri);
        setting.setClosedSaturday(sat);
        setting.setClosedSunday(sun);
        weeklyClosureSettingRepository.save(setting);
    }
}
