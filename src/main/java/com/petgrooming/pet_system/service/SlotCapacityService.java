package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.SlotCapacity;
import com.petgrooming.pet_system.repository.SlotCapacityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 需求 3：時段名額控管（同時段最多 5 隻），併發安全。
 *
 * 兩段式設計：
 *  1) ensureSlot()：以 REQUIRES_NEW 獨立交易「確保這一列存在」。
 *     若同時有人也在建立，靠 unique 約束擋掉重複，捕捉例外後略過即可。
 *     用獨立交易的原因：unique 違反會 rollback，不能污染外層預約主交易。
 *  2) reserve() / release()：在外層預約交易內，對該列加悲觀鎖後 +1 / -1。
 *     鎖會持有到外層交易 commit，因此同時段的並發預約會完全序列化，杜絕超賣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotCapacityService {

    public static final int DEFAULT_CAPACITY = 5;

    private final SlotCapacityRepository slotRepo;

    /**
     * 確保 (date, time) 的計數列存在。獨立交易，冪等。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureSlot(LocalDate date, LocalTime time) {
        if (slotRepo.findBySlotDateAndSlotTime(date, time).isPresent()) return;
        try {
            slotRepo.save(SlotCapacity.builder()
                    .slotDate(date)
                    .slotTime(time)
                    .booked(0)
                    .capacity(DEFAULT_CAPACITY)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 併發下另一交易已插入，忽略即可（列已存在）
            log.debug("時段 {} {} 已由並發交易建立，略過", date, time);
        }
    }

    /**
     * 預約時佔用一個名額。必須在外層預約交易內呼叫。
     * @throws IllegalStateException 已額滿時拋出
     */
    @Transactional
    public void reserve(LocalDate date, LocalTime time) {
        SlotCapacity slot = slotRepo.lockSlot(date, time)
                .orElseThrow(() -> new IllegalStateException("時段計數列不存在，請先 ensureSlot"));

        if (slot.getBooked() >= slot.getCapacity()) {
            throw new IllegalStateException("此時段已額滿（上限 " + slot.getCapacity() + " 隻）");
        }
        slot.setBooked(slot.getBooked() + 1);
        slotRepo.save(slot);
    }

    /**
     * 取消預約時釋放一個名額。
     */
    @Transactional
    public void release(LocalDate date, LocalTime time) {
        slotRepo.lockSlot(date, time).ifPresent(slot -> {
            if (slot.getBooked() > 0) {
                slot.setBooked(slot.getBooked() - 1);
                slotRepo.save(slot);
            }
        });
    }

    /**
     * 查某時段目前已預約數（顯示剩餘名額用，不加鎖）。
     */
    public int bookedCount(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time)
                .map(SlotCapacity::getBooked).orElse(0);
    }

    /**
     * 查某時段目前的名額上限（沒有手動調整過的話回傳預設值 5）。
     */
    public int getCapacity(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time)
                .map(SlotCapacity::getCapacity).orElse(DEFAULT_CAPACITY);
    }

    /**
     * 需求 1：店家手動調整「特定日期＋特定時段」的名額上限，或直接關閉（設為 0）。
     * 只影響「剩餘可預約名額」，已經預約成功的紀錄（booked 數）完全不受影響——
     * 即使把 capacity 調到比 booked 還低，現有預約仍然有效，只是不能再新增預約進這個時段。
     * 這個調整是單次生效（只影響這一天這個時段），不會套用重複規則。
     */
    @Transactional
    public void setCapacity(LocalDate date, LocalTime time, int newCapacity) {
        if (newCapacity < 0) {
            throw new IllegalArgumentException("名額上限不能小於 0");
        }
        ensureSlot(date, time);
        SlotCapacity slot = slotRepo.findBySlotDateAndSlotTime(date, time)
                .orElseThrow(() -> new IllegalStateException("時段建立失敗"));
        slot.setCapacity(newCapacity);
        slotRepo.save(slot);
    }
}
