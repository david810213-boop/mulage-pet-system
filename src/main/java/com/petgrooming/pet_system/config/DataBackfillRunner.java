package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.SlotCapacity;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.SlotCapacityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 因為本專案以 Hibernate ddl-auto 管理 schema（無 Flyway），
 * 新增欄位 / 新表只會被自動建立，但「既有資料的回填」需要程式補做。
 * 這個 Runner 開機時執行一次，等同一份輕量 migration。
 *
 * 內容：
 *  1) 需求 3：依既有「未取消」預約，重建 slot_capacity 計數，讓時段名額正確。
 *  2) 需求 4：把名稱含「大美容 / 小美容 / 精緻洗 / 定製洗」的項目標記為可線上預約。
 *
 * 冪等：可重複執行。跑過並確認資料無誤後，可移除本類別。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataBackfillRunner implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final GroomingItemRepository groomingItemRepository;

    private static final List<String> BOOKABLE_KEYWORDS =
            List.of("大美容", "小美容", "精緻洗", "定製洗", "定制洗");

    @Override
    @Transactional
    public void run(String... args) {
        backfillSlotCapacity();
        backfillBookableItems();
    }

    // 需求 3：重建時段計數
    private void backfillSlotCapacity() {
        List<Appointment> active = appointmentRepository.findAll().stream()
                .filter(a -> !a.isCancelled())
                .collect(Collectors.toList());

        // 依 (date, startTime) 分組計數
        Map<String, Long> counts = active.stream().collect(Collectors.groupingBy(
                a -> a.getDate() + "|" + a.getStartTime(), Collectors.counting()));

        int created = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            LocalTime time = LocalTime.parse(parts[1]);
            int booked = e.getValue().intValue();

            SlotCapacity slot = slotCapacityRepository
                    .findBySlotDateAndSlotTime(date, time)
                    .orElseGet(() -> SlotCapacity.builder()
                            .slotDate(date).slotTime(time).capacity(5).booked(0).build());

            // 只在計數與現況不同時更新（冪等）
            if (slot.getBooked() != booked) {
                slot.setBooked(booked);
                slotCapacityRepository.save(slot);
                created++;
            } else if (slot.getId() == null) {
                slotCapacityRepository.save(slot);
                created++;
            }
        }
        if (created > 0) log.info("[Backfill] slot_capacity 重建 {} 個時段計數", created);
    }

    // 需求 4：標記可預約主項目
    private void backfillBookableItems() {
        List<GroomingItem> items = groomingItemRepository.findByIsDeletedFalse();
        int marked = 0;
        for (GroomingItem item : items) {
            if (item.isBookable()) continue;
            String name = item.getName() == null ? "" : item.getName();
            boolean match = BOOKABLE_KEYWORDS.stream().anyMatch(name::contains);
            if (match) {
                item.setBookable(true);
                groomingItemRepository.save(item);
                marked++;
            }
        }
        if (marked > 0) log.info("[Backfill] 標記 {} 個可線上預約項目", marked);
    }
}
