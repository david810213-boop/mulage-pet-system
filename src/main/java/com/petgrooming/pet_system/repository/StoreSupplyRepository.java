package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.StoreSupply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreSupplyRepository extends JpaRepository<StoreSupply, Long> {

    List<StoreSupply> findByIsDeletedFalseOrderByNameAsc();

    // 需求（追加，2026-10-02）：條碼查詢／CSV 匯入比對
    java.util.Optional<StoreSupply> findByBarcode(String barcode);
    java.util.Optional<StoreSupply> findFirstByNameAndIsDeletedFalse(String name);
    List<StoreSupply> findByIsDeletedFalseAndBarcodeIsNull();
}
