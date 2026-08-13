package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.ClosedDate;
import com.petgrooming.pet_system.repository.ClosedDateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 需求 16：公休日設定。
 */
@Service
@RequiredArgsConstructor
public class ClosedDateService {

    private final ClosedDateRepository closedDateRepository;

    public boolean isClosed(LocalDate date) {
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
}
