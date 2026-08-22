package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 需求（追加）：「這隻寵物是不是既有客戶（有沒有任何一筆已結帳消費紀錄）」的共用判斷邏輯。
 *
 * 原本這段邏輯寫在 {@link DogFirstVisitDiscountService} 裡（判斷狗狗首次體驗優惠用），
 * 現在「僅限既有客戶」項目限制（例如貓咪基礎保養）也需要同一套判斷，所以抽成獨立的共用
 * service，兩邊都呼叫這裡，只維護一份。
 */
@Service
@RequiredArgsConstructor
public class PetConsumptionHistoryService {

    private final AppointmentRepository appointmentRepository;
    private final WalkInOrderRepository walkInOrderRepository;

    /**
     * 這隻寵物（同一飼主 + 同寵物名）名下有沒有任何一筆已結帳的預約或現場開單。
     *
     * @param excludeId 排除正在處理的這一筆本身（防呆用；不需要排除就傳 null）
     */
    public boolean hasPriorPaidService(Long personId, String petName, Long excludeId) {
        boolean hasAppointment = appointmentRepository.findByUserIdAndPetNameAndPaidTrue(personId, petName)
                .stream()
                .anyMatch(a -> excludeId == null || !a.getId().equals(excludeId));
        if (hasAppointment) return true;

        return walkInOrderRepository.findByMemberIdAndPetNameAndPaidTrue(personId, petName)
                .stream()
                .anyMatch(w -> excludeId == null || !w.getId().equals(excludeId));
    }

    /** 反過來問「是不是既有客戶」，語意比 hasPriorPaidService 直觀，內部就是同一個查詢。 */
    public boolean isExistingCustomer(Long personId, String petName, Long excludeId) {
        return hasPriorPaidService(personId, petName, excludeId);
    }
}
