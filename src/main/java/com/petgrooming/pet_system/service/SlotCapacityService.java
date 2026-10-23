package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.model.SlotCapacity;
import com.petgrooming.pet_system.repository.SlotCapacityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

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

    private final SlotCapacityRepository slotRepo;
    // 需求（追加，2026-08-27）：時段容量範本——新建立一天的時段計數列時，
    // 初始上限改成讀這份範本（每個時段可以分別設定不同預設值），
    // 不再是所有時段統一寫死同一個數字。
    private final DefaultSlotCapacityTemplateService defaultSlotCapacityTemplateService;
    private final EntityManager entityManager;

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
                    .capacity(defaultSlotCapacityTemplateService.getCapacity(time))
                    // 需求（追加，2026-09-08）：新建立的一天，物種限制初始值也從範本帶入
                    .allowedPetType(defaultSlotCapacityTemplateService.getAllowedPetType(time))
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
        SlotCapacity slot = lockSlot(date, time)
                .orElseThrow(() -> new AppointmentException("時段計數列不存在，請先 ensureSlot"));

        if (slot.getBooked() >= slot.getCapacity()) {
            throw new AppointmentException("此時段已額滿（上限 " + slot.getCapacity() + " 隻）");
        }
        slot.setBooked(slot.getBooked() + 1);
        slotRepo.save(slot);
    }

    /**
     * 取消預約時釋放一個名額。
     */
    @Transactional
    public void release(LocalDate date, LocalTime time) {
        lockSlot(date, time).ifPresent(slot -> {
            if (slot.getBooked() > 0) {
                slot.setBooked(slot.getBooked() - 1);
                slotRepo.save(slot);
            }
        });
    }

    /**
     * 查某時段目前已預約數（顯示剩餘名額用，不加鎖）。
     */
    /**
     * 取得時段計數列並加悲觀寫鎖，鎖持有到本交易 commit，讓同時段的預約依序扣名額。
     *
     * 必須用 refresh 搭配鎖定模式重新讀取：預約流程會在同一交易內先讀取時段（檢查限定物種），
     * 一般的鎖定查詢雖然會取得列鎖，Hibernate 卻會回傳快取中的舊名額；
     * 而不帶鎖的重新讀取在 MySQL 可重複讀隔離層級下只會讀到交易開始時的快照。
     */
    private Optional<SlotCapacity> lockSlot(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time).map(slot -> {
            entityManager.refresh(slot, LockModeType.PESSIMISTIC_WRITE);
            return slot;
        });
    }

    public int bookedCount(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time)
                .map(SlotCapacity::getBooked).orElse(0);
    }

    /**
     * 查某時段目前的名額上限（該日該時段還沒建立計數列的話，回傳範本裡設定的預設值）。
     */
    public int getCapacity(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time)
                .map(SlotCapacity::getCapacity)
                .orElseGet(() -> defaultSlotCapacityTemplateService.getCapacity(time));
    }

    /**
     * 需求（追加，2026-09-08）：查某時段目前限定的物種（null＝不限制），該日該時段
     * 還沒建立計數列的話，回傳範本裡設定的預設值。
     */
    public com.petgrooming.pet_system.enums.PetType getAllowedPetType(LocalDate date, LocalTime time) {
        return slotRepo.findBySlotDateAndSlotTime(date, time)
                .map(SlotCapacity::getAllowedPetType)
                .orElseGet(() -> defaultSlotCapacityTemplateService.getAllowedPetType(time));
    }

    /**
     * 需求 1：店家手動調整「特定日期＋特定時段」的名額上限，或直接關閉（設為 0）。
     * 需求（追加，2026-09-08）：同時可以覆寫這一天這個時段限定的物種（null＝不限制）。
     * 只影響「剩餘可預約名額」與「這個時段能不能約」，已經預約成功的紀錄（booked 數）
     * 完全不受影響——即使把 capacity 調到比 booked 還低、或改成限定物種跟現有預約的
     * 寵物不符，現有預約仍然有效，只是不能再新增預約進這個時段。
     * 這個調整是單次生效（只影響這一天這個時段），不會套用重複規則。
     */
    @Transactional
    public void setCapacity(LocalDate date, LocalTime time, int newCapacity,
                             com.petgrooming.pet_system.enums.PetType allowedPetType) {
        if (newCapacity < 0) {
            throw new AppointmentException("名額上限不能小於 0");
        }
        ensureSlot(date, time);
        SlotCapacity slot = slotRepo.findBySlotDateAndSlotTime(date, time)
                .orElseThrow(() -> new AppointmentException("時段建立失敗"));
        slot.setCapacity(newCapacity);
        slot.setAllowedPetType(allowedPetType);
        slotRepo.save(slot);
    }

    /** 相容舊呼叫端（不調整物種限制）。 */
    @Transactional
    public void setCapacity(LocalDate date, LocalTime time, int newCapacity) {
        setCapacity(date, time, newCapacity, getAllowedPetType(date, time));
    }
}