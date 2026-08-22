package com.petgrooming.pet_system.service;

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
                .orElseThrow(() -> new IllegalArgumentException("找不到商品 #" + id));
    }

    @Transactional
    public RetailProduct create(String name, int price, int stockQuantity, String description, int unitCost) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("商品名稱不可為空");
        if (price < 0) throw new IllegalArgumentException("售價不可為負數");
        if (stockQuantity < 0) throw new IllegalArgumentException("庫存量不可為負數");
        if (unitCost < 0) throw new IllegalArgumentException("成本不可為負數");
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
        if (name == null || name.isBlank()) throw new IllegalArgumentException("商品名稱不可為空");
        if (price < 0) throw new IllegalArgumentException("售價不可為負數");
        if (unitCost < 0) throw new IllegalArgumentException("成本不可為負數");
        product.setName(name.trim());
        product.setPrice(price);
        product.setDescription(description == null || description.isBlank() ? null : description.trim());
        product.setUnitCost(unitCost);
        retailProductRepository.save(product);
    }

    // 手動調整庫存（進貨補貨、盤點修正）。delta 可正可負。
    @Transactional
    public void adjustStock(Long id, int delta) {
        RetailProduct product = getById(id);
        int newQuantity = product.getStockQuantity() + delta;
        if (newQuantity < 0) throw new IllegalArgumentException("庫存量不可調整為負數（目前庫存 " + product.getStockQuantity() + "）");
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
    @Transactional
    public void deductStock(Long id, int quantity) {
        if (quantity <= 0) return;
        RetailProduct product = getById(id);
        int remaining = product.getStockQuantity() - quantity;
        if (remaining < 0) {
            throw new IllegalArgumentException("「" + product.getName() + "」庫存不足（剩餘 "
                    + product.getStockQuantity() + "，需要 " + quantity + "）");
        }
        product.setStockQuantity(remaining);
        retailProductRepository.save(product);
    }
}
