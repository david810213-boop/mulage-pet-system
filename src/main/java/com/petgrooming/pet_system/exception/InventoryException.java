package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 庫存相關業務錯誤：零售商品（RetailProductService）、店用洗劑
 * （StoreSupplyService）的庫存管理、扣庫存、上下架等。
 */
public class InventoryException extends BusinessException {
    public InventoryException(String message) {
        super(message);
    }

    public InventoryException(String message, HttpStatus status) {
        super(message, status);
    }
}
