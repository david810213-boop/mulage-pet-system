package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.InventoryException;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.support.Concurrently;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailProductServiceTest extends IntegrationTestBase {

    @Autowired
    private RetailProductService retailProductService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void deductStockBeyondRemainingIsRejected() {
        RetailProduct product = newProduct(2);

        assertThatThrownBy(() -> retailProductService.deductStock(product.getId(), 3))
                .isInstanceOf(InventoryException.class);
        assertThat(stockOf(product)).isEqualTo(2);
    }

    @Test
    void stockCannotBeAdjustedBelowZero() {
        RetailProduct product = newProduct(1);

        assertThatThrownBy(() -> retailProductService.adjustStock(product.getId(), -2))
                .isInstanceOf(InventoryException.class);
        assertThat(stockOf(product)).isEqualTo(1);
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        RetailProduct product = newProduct(3);

        Concurrently.Result result = Concurrently.run(8,
                () -> retailProductService.deductStock(product.getId(), 1));

        assertThat(result.succeeded()).isEqualTo(3);
        assertThat(stockOf(product)).isZero();
    }

    /**
     * 模擬結帳流程：同一個交易內先讀取商品（顯示名稱、價格），再扣庫存。
     * 商品已在交易內被讀取過時，鎖定查詢仍必須拿到最新庫存。
     */
    @Test
    void concurrentSalesAfterReadingProductInSameTransactionNeverOversell() throws Exception {
        RetailProduct product = newProduct(3);

        Concurrently.Result result = Concurrently.run(8, () -> transactionTemplate.executeWithoutResult(status -> {
            retailProductService.getById(product.getId());
            retailProductService.deductStock(product.getId(), 1);
        }));

        assertThat(result.succeeded()).isEqualTo(3);
        assertThat(stockOf(product)).isZero();
    }

    /** 同一交易內對同一商品先後扣兩次庫存（同一張單買兩件），兩次都必須計入。 */
    @Test
    void twoDeductionsInOneTransactionBothCount() {
        RetailProduct product = newProduct(5);

        transactionTemplate.executeWithoutResult(status -> {
            retailProductService.deductStock(product.getId(), 1);
            retailProductService.deductStock(product.getId(), 2);
        });

        assertThat(stockOf(product)).isEqualTo(2);
    }

    private RetailProduct newProduct(int stock) {
        return retailProductService.create("測試商品-" + UUID.randomUUID().toString().substring(0, 8),
                100, stock, null, 50);
    }

    private int stockOf(RetailProduct product) {
        return retailProductService.getById(product.getId()).getStockQuantity();
    }
}
