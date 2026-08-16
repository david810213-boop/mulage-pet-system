package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.StoreSupply;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.notification.LineMessagingService;
import com.petgrooming.pet_system.repository.StoreSupplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求 7-2：店用洗劑管理——領用登記扣庫存、低於安全庫存量自動發 LINE 通知全體人員。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreSupplyService {

    private final StoreSupplyRepository storeSupplyRepository;
    private final UserService userService;
    private final LineMessagingService lineMessagingService;
    private final com.petgrooming.pet_system.repository.SupplyUsageRecordRepository supplyUsageRecordRepository; // 需求 6

    public List<StoreSupply> listActive() {
        return storeSupplyRepository.findByIsDeletedFalseOrderByNameAsc();
    }

    public StoreSupply getById(Long id) {
        return storeSupplyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到店用洗劑 #" + id));
    }

    @Transactional
    public StoreSupply create(String name, int stockQuantity, int safetyStockThreshold, int unitCost) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("品名不可為空");
        if (stockQuantity < 0 || safetyStockThreshold < 0 || unitCost < 0) {
            throw new IllegalArgumentException("數量/成本不可為負數");
        }
        return storeSupplyRepository.save(StoreSupply.builder()
                .name(name.trim())
                .stockQuantity(stockQuantity)
                .safetyStockThreshold(safetyStockThreshold)
                .unitCost(unitCost)
                .build());
    }

    @Transactional
    public void update(Long id, String name, int safetyStockThreshold, int unitCost) {
        StoreSupply supply = getById(id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("品名不可為空");
        if (safetyStockThreshold < 0 || unitCost < 0) throw new IllegalArgumentException("數量/成本不可為負數");
        supply.setName(name.trim());
        supply.setSafetyStockThreshold(safetyStockThreshold);
        supply.setUnitCost(unitCost);
        storeSupplyRepository.save(supply);
    }

    // 進貨補貨：只會讓庫存變多，不會觸發低庫存通知（進貨當然是庫存變充足，不是變少）
    @Transactional
    public void restock(Long id, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("進貨數量必須大於 0");
        StoreSupply supply = getById(id);
        supply.setStockQuantity(supply.getStockQuantity() + quantity);
        storeSupplyRepository.save(supply);
        log.info("店用洗劑「{}」進貨 +{}，目前庫存 {}", supply.getName(), quantity, supply.getStockQuantity());
    }

    // ── 需求 7-2：員工領用登記，扣庫存；低於安全庫存量自動發 LINE 通知全體人員叫貨 ──
    @Transactional
    public void recordUsage(Long id, int quantity, String staffUsername, String note) {
        if (quantity <= 0) throw new IllegalArgumentException("領用數量必須大於 0");
        StoreSupply supply = getById(id);
        int remaining = supply.getStockQuantity() - quantity;
        if (remaining < 0) {
            throw new IllegalArgumentException("「" + supply.getName() + "」庫存不足（剩餘 "
                    + supply.getStockQuantity() + "，需要 " + quantity + "）");
        }
        supply.setStockQuantity(remaining);
        storeSupplyRepository.save(supply);

        User staff = userService.getUserEntityByUsername(staffUsername);
        log.info("店用洗劑「{}」領用 -{}，領用人：{}，目前庫存 {}",
                supply.getName(), quantity, staff.getName(), remaining);

        // 需求 6：另外存一份結構化紀錄（不是只寫操作紀錄的文字備註），
        // 成本用「領用當下」的單價快照，財務報表算成本才不會受之後調價影響。
        supplyUsageRecordRepository.save(com.petgrooming.pet_system.model.SupplyUsageRecord.builder()
                .supplyId(supply.getId())
                .supplyName(supply.getName())
                .quantity(quantity)
                .unitCostSnapshot(supply.getUnitCost())
                .usedByUsername(staff.getUsername())
                .usedByName(staff.getName())
                .note(note)
                .build());

        if (remaining <= supply.getSafetyStockThreshold()) {
            notifyLowStock(supply);
        }
    }

    // 低庫存通知：發給所有有綁定 LINE 的店家/員工帳號
    // ⚠️ 前提：店員帳號要有綁定 LINE（lineUserId），目前系統的 LINE 登入預設是給會員用的，
    // 店員多半用帳號密碼登入、沒有綁 LINE——沒收到通知不代表功能沒動，可能是帳號還沒綁 LINE。
    private void notifyLowStock(StoreSupply supply) {
        String text = String.format(
                "⚠️【庫存不足提醒】慕沐村店用洗劑「%s」剩餘 %d（安全庫存量 %d），請盡快叫貨補貨。",
                supply.getName(), supply.getStockQuantity(), supply.getSafetyStockThreshold());

        List<User> staffAndAdmin = new ArrayList<>(userService.getAllStaffEntities());
        staffAndAdmin.addAll(userService.getAllAdminEntities());

        int notified = 0;
        for (User u : staffAndAdmin) {
            if (u.getLineUserId() != null && !u.getLineUserId().isBlank()) {
                lineMessagingService.pushText(u.getLineUserId(), text);
                notified++;
            }
        }
        log.info("店用洗劑「{}」低於安全庫存，已通知 {} 位有綁定 LINE 的員工/店家", supply.getName(), notified);
    }

    @Transactional
    public void softDelete(Long id) {
        StoreSupply supply = getById(id);
        supply.setDeleted(true);
        storeSupplyRepository.save(supply);
    }
}
