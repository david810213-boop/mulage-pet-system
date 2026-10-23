package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.InventoryException;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 需求 7-1：零售商品管理。
 */
@Service
@RequiredArgsConstructor
public class RetailProductService {

    private final RetailProductRepository retailProductRepository;
    private final BarcodeService barcodeService; // 需求（2026-10-02）：條碼
    private final EntityManager entityManager;

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
    public int bulkUpdateCost(Map<Long, Integer> idToCost) {
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
                .orElseThrow(() -> new InventoryException(ErrorCode.NOT_FOUND, "找不到商品 #" + id));
    }

    @Transactional
    public RetailProduct create(String name, int price, int stockQuantity, String description, int unitCost) {
        return create(name, price, stockQuantity, description, unitCost, null);
    }

    // 需求（2026-10-02 店家確認 7-2-C）：有原廠條碼就填，沒填由系統產生店內條碼
    @Transactional
    public RetailProduct create(String name, int price, int stockQuantity, String description, int unitCost,
            String barcode) {
        String code = BarcodeService.normalize(barcode);
        barcodeService.assertAvailable(code, BarcodeService.TYPE_RETAIL, null);
        RetailProduct saved = createNoBarcode(name, price, stockQuantity, description, unitCost);
        saved.setBarcode(code != null ? code : BarcodeService.generate(BarcodeService.TYPE_RETAIL, saved.getId()));
        return retailProductRepository.save(saved);
    }

    private RetailProduct createNoBarcode(String name, int price, int stockQuantity, String description, int unitCost) {
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
        update(id, name, price, description, unitCost, getById(id).getBarcode());
    }

    // 需求（2026-10-02）：可修改條碼（留空＝改回系統產生的店內條碼）
    @Transactional
    public void update(Long id, String name, int price, String description, int unitCost, String barcode) {
        RetailProduct product = getById(id);
        String code = BarcodeService.normalize(barcode);
        if (code == null) code = BarcodeService.generate(BarcodeService.TYPE_RETAIL, id);
        barcodeService.assertAvailable(code, BarcodeService.TYPE_RETAIL, id);
        product.setBarcode(code);
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
    @Transactional
    public void adjustStock(Long id, int delta) {
        RetailProduct product = lockProduct(id);
        int newQuantity = product.getStockQuantity() + delta;
        if (newQuantity < 0) throw new InventoryException("庫存量不可調整為負數（目前庫存 " + product.getStockQuantity() + "）");
        product.setStockQuantity(newQuantity);
        retailProductRepository.save(product);
    }

    // ── 需求（追加，2026-10-02 店家確認 7-6）：盤點——輸入實際數量，回傳差額 ──
    @Transactional
    public int stocktake(Long id, int actual) {
        if (actual < 0) throw new InventoryException("實際數量不可為負數");
        RetailProduct product = lockProduct(id);
        int diff = actual - product.getStockQuantity();
        product.setStockQuantity(actual);
        retailProductRepository.save(product);
        return diff;
    }

    /**
     * 取得商品並加悲觀寫鎖，鎖持有到本交易 commit，讓同一商品的扣庫存／調整／盤點依序執行，
     * 避免兩筆結帳都讀到「還有 1 件」而多賣。商品可能已在本交易被讀取過（例如結帳時先顯示品名）。
     *
     * 做法：先 flush 本交易尚未寫入的修改、把快取中的舊實體移除，再以 SELECT ... FOR UPDATE 讀取。
     * 鎖定讀取永遠讀最新已提交資料（含本交易自己的寫入），也不會拿到快取中的舊值。
     * 不可改用 refresh：實體已持有鎖時 Hibernate 會改發一般查詢，在 MySQL 可重複讀隔離層級下
     * 只讀到交易開始時的快照；也不可只用鎖定查詢：實體已在快取中時 Hibernate 會回傳舊值。
     */
    private RetailProduct lockProduct(Long id) {
        entityManager.flush();
        retailProductRepository.findById(id).ifPresent(entityManager::detach);
        return retailProductRepository.lockById(id)
                .orElseThrow(() -> new InventoryException(ErrorCode.NOT_FOUND, "找不到商品 #" + id));
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
        RetailProduct product = lockProduct(id);
        int remaining = product.getStockQuantity() - quantity;
        if (remaining < 0) {
            throw new InventoryException("「" + product.getName() + "」庫存不足（剩餘 "
                    + product.getStockQuantity() + "，需要 " + quantity + "）");
        }
        product.setStockQuantity(remaining);
        retailProductRepository.save(product);
    }
}
