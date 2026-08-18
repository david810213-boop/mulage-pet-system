package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.RetailProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetailProductRepository extends JpaRepository<RetailProduct, Long> {

    List<RetailProduct> findByIsDeletedFalseOrderByNameAsc();

    // 需求 7-2 之後會用來查低庫存商品，先建起來
    List<RetailProduct> findByIsDeletedFalseAndStockQuantityLessThan(int threshold);
}
