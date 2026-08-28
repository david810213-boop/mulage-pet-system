package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.config.BusinessHours;
import com.petgrooming.pet_system.model.DefaultSlotCapacityTemplate;
import com.petgrooming.pet_system.repository.DefaultSlotCapacityTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 需求（追加，2026-08-27）：預設時段容量範本管理。
 *
 * 通用一份範本（不分星期幾），每個時段格線（依 BusinessHours 產生）對應一個預設名額上限。
 * 沿用專案裡既有單例設定（例如 WeeklyClosureSetting）的「lazy 自我初始化」寫法：
 * 第一次讀取某個時段時，如果資料庫還沒有這一列，就用預設值建立一列，不需要另外寫
 * Flyway 遷移檔或啟動時的批次種子資料——這份設定本來就是店家自己會在後台調整的東西，
 * 不是像 GroomingItem 那種固定不變的種子資料。
 */
@Service
@RequiredArgsConstructor
public class DefaultSlotCapacityTemplateService {

    /** 資料庫裡完全沒有任何範本設定時的保險預設值（正常情況下第一次載入後台頁面就會補齊每一格）。 */
    private static final int FALLBACK_CAPACITY = 5;

    private final DefaultSlotCapacityTemplateRepository templateRepository;

    /**
     * 依 BusinessHours 的營業時間／時段長度，產生完整的時段格線（例如 11:00, 11:30, 12:00 ... 18:30）。
     */
    public List<LocalTime> generateStandardSlotTimes() {
        List<LocalTime> times = new ArrayList<>();
        LocalTime current = BusinessHours.OPENING;
        while (current.isBefore(BusinessHours.CLOSING)) {
            times.add(current);
            current = current.plusMinutes(BusinessHours.SLOT_MINUTES);
        }
        return times;
    }

    /**
     * 查某個時段目前的預設名額上限，資料庫裡還沒有這一列的話回傳保險預設值（不會自動寫入，
     * 純查詢用途，例如 SlotCapacityService 建立新的一天的時段列時使用）。
     */
    public int getCapacity(LocalTime slotTime) {
        return templateRepository.findBySlotTime(slotTime)
                .map(DefaultSlotCapacityTemplate::getCapacity)
                .orElse(FALLBACK_CAPACITY);
    }

    /**
     * 後台頁面顯示用：回傳完整時段格線清單，資料庫裡還沒有的時段自動補一列預設值進去
     * （lazy 自我初始化，跟 ClosedDateService.getWeeklyClosureSetting() 同一套寫法），
     * 這樣店家在後台看到的一定是完整的一份表格，不會有缺格。
     */
    @Transactional
    public List<DefaultSlotCapacityTemplate> listForAdmin() {
        List<DefaultSlotCapacityTemplate> result = new ArrayList<>();
        for (LocalTime time : generateStandardSlotTimes()) {
            DefaultSlotCapacityTemplate row = templateRepository.findBySlotTime(time)
                    .orElseGet(() -> templateRepository.save(
                            DefaultSlotCapacityTemplate.builder()
                                    .slotTime(time)
                                    .capacity(FALLBACK_CAPACITY)
                                    .build()));
            result.add(row);
        }
        return result;
    }

    /**
     * 店家在後台調整某個時段的預設名額上限。
     * ⚠️ 這只會影響「以後新建立的日期」在第一次被查詢/預約時的初始上限，
     * 已經存在的 SlotCapacity 列（不管是已經有人預約過、還是店家之前手動覆寫過的日期）
     * 不會被這個調整回溯影響——跟「調整名額上限」（單一天覆寫）是分開的兩件事。
     */
    @Transactional
    public void setCapacity(LocalTime slotTime, int newCapacity) {
        if (newCapacity < 0) {
            throw new IllegalArgumentException("名額上限不能小於 0");
        }
        DefaultSlotCapacityTemplate row = templateRepository.findBySlotTime(slotTime)
                .orElseGet(() -> DefaultSlotCapacityTemplate.builder().slotTime(slotTime).build());
        row.setCapacity(newCapacity);
        templateRepository.save(row);
    }
}
