package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.InventoryException;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 需求 7-1：零售商品管理。
 */
@Service
@RequiredArgsConstructor
public class RetailProductService {

    private final RetailProductRepository retailProductRepository;

    public List<RetailProduct> listActive() {
        return retailProductRepository.findByIsDeletedFalseOrderByNameAsc();
    }

    // 需求（追加）：成本回填清單，只列出 unitCost 還是 0 的上架商品
    public List<RetailProduct> listPendingCostBackfill() {
        return retailProductRepository.findByIsDeletedFalseAndUnitCostOrderByNameAsc(0);
    }

    // 需求（追加）：批次回填成本——一次送整頁的 id→成本 對照表，逐筆寫入。
    // 跳過負數（防呆，理論上前端 input min="0" 已經擋掉，這裡後端再擋一次）
    // 跟金額沒填/沒改的（維持 0，不強迫店家一定要填滿整頁才能送出，可以分批補）。
    // 回傳實際更新的筆數，讓畫面能提示店家「這次補了幾筆」。
    @Transactional
    public int bulkUpdateCost(java.util.Map<Long, Integer> idToCost) {
        int updated = 0;
        for (var entry : idToCost.entrySet()) {
            Integer cost = entry.getValue();
            if (cost == null || cost <= 0) continue; // 沒填或填 0 就跳過，不算一次更新
            RetailProduct product = retailProductRepository.findById(entry.getKey()).orElse(null);
            if (product == null || product.isDeleted()) continue;
            product.setUnitCost(cost);
            retailProductRepository.save(product);
            updated++;
        }
        return updated;
    }

    public RetailProduct getById(Long id) {
        return retailProductRepository.findById(id)
                .orElseThrow(() -> new InventoryException("找不到商品 #" + id));
    }

    @Transactional
    public RetailProduct create(String name, int price, int stockQuantity, String description, int unitCost) {
        if (name == null || name.isBlank()) throw new InventoryException("商品名稱不可為空");
        if (price < 0) throw new InventoryException("售價不可為負數");
        if (stockQuantity < 0) throw new InventoryException("庫存量不可為負數");
        if (unitCost < 0) throw new InventoryException("成本不可為負數");
        return retailProductRepository.save(RetailProduct.builder()
                .name(name.trim())
                .price(price)
                .stockQuantity(stockQuantity)
                .description(description == null || description.isBlank() ? null : description.trim())
                .unitCost(unitCost)
                .build());
    }

    @Transactional
    public void update(Long id, String name, int price, String description, int unitCost) {
        RetailProduct product = getById(id);
        if (name == null || name.isBlank()) throw new InventoryException("商品名稱不可為空");
        if (price < 0) throw new InventoryException("售價不可為負數");
        if (unitCost < 0) throw new InventoryException("成本不可為負數");
        product.setName(name.trim());
        product.setPrice(price);
        product.setDescription(description == null || description.isBlank() ? null : description.trim());
        product.setUnitCost(unitCost);
        retailProductRepository.save(product);
    }

    // 手動調整庫存（進貨補貨、盤點修正）。delta 可正可負。
    // 需求（修正，2026-09-13）：改用 lockById() 取代 getById()，加悲觀寫鎖，
    // 避免跟 deductStock() 或另一筆調整同時發生時，兩邊各自讀到舊庫存數字。
    @Transactional
    public void adjustStock(Long id, int delta) {
        RetailProduct product = retailProductRepository.lockById(id)
                .orElseThrow(() -> new InventoryException("找不到商品 #" + id));
        int newQuantity = product.getStockQuantity() + delta;
        if (newQuantity < 0) throw new InventoryException("庫存量不可調整為負數（目前庫存 " + product.getStockQuantity() + "）");
        product.setStockQuantity(newQuantity);
        retailProductRepository.save(product);
    }

    @Transactional
    public void softDelete(Long id) {
        RetailProduct product = getById(id);
        product.setDeleted(true);
        retailProductRepository.save(product);
    }

    // ── 結帳扣庫存 ──────────────────────────────────────────────────────
    // 需求 7-1：結帳成功才真的扣庫存（避免開單到一半、還沒結帳就先扣掉，
    // 之後如果這筆訂單被取消或一直沒結帳，庫存會對不上）。
    // 需求（修正，2026-09-13）：改用 lockById() 取代 getById()，對這筆商品加
    // 悲觀寫鎖（鎖持有到本次結帳交易 commit 為止）。原本的寫法是先讀庫存數字
    // 再判斷夠不夠扣、最後寫回，沒有鎖——如果現場同時開兩張單結帳同一件商品
    // （例如只剩最後 1 件），兩筆交易可能都讀到「還有 1 件」、都判斷可以賣，
    // 最後都扣成功，庫存會變成負數，等於多賣出一件不存在的商品。加鎖後第二筆
    // 交易會排隊等第一筆真正寫回庫存、交易 commit 之後，才讀到最新數字，
    // 這時候如果真的不夠扣就會正確擋下。跟 WalletService 扣款、
    // SlotCapacityService 預約時段是同一套防超賣/超扣的寫法。
    @Transactional
    public void deductStock(Long id, int quantity) {
        if (quantity <= 0) return;
        RetailProduct product = retailProductRepository.lockById(id)
                .orElseThrow(() -> new InventoryException("找不到商品 #" + id));
        int remaining = product.getStockQuantity() - quantity;
        if (remaining < 0) {
            throw new InventoryException("「" + product.getName() + "」庫存不足（剩餘 "
                    + product.getStockQuantity() + "，需要 " + quantity + "）");
        }
        product.setStockQuantity(remaining);
        retailProductRepository.save(product);
    }
}
