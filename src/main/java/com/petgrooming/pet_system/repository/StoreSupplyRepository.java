package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.StoreSupply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreSupplyRepository extends JpaRepository<StoreSupply, Long> {

    List<StoreSupply> findByIsDeletedFalseOrderByNameAsc();

    // 條碼查詢／CSV 匯入比對
    Optional<StoreSupply> findByBarcode(String barcode);
    Optional<StoreSupply> findFirstByNameAndIsDeletedFalse(String name);
    List<StoreSupply> findByIsDeletedFalseAndBarcodeIsNull();
}
