package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 獎金級距（後台可編輯）：月度積分對應的獎勵金。
 */
@Service
@RequiredArgsConstructor
public class BonusTierService {

    private final BonusTierRepository bonusTierRepository;

    // ── 獎金級距管理（後台可編輯）────────────────────────────────────
    public List<BonusTier> getAllBonusTiers() {
        return bonusTierRepository.findAllByOrderByMinPointsAsc();
    }

    @Transactional
    public BonusTier createBonusTier(int minPoints, int maxPoints, int bonusAmount) {
        if (minPoints > maxPoints) {
            throw new PerformanceException("下限不能大於上限");
        }
        return bonusTierRepository.save(
                BonusTier.builder().minPoints(minPoints).maxPoints(maxPoints).bonusAmount(bonusAmount).build());
    }

    @Transactional
    public void updateBonusTier(Long id, int minPoints, int maxPoints, int bonusAmount) {
        if (minPoints > maxPoints) {
            throw new PerformanceException("下限不能大於上限");
        }
        BonusTier tier = bonusTierRepository.findById(id)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到級距 #" + id));
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
