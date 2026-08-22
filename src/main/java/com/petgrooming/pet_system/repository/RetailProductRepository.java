package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.RetailProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetailProductRepository extends JpaRepository<RetailProduct, Long> {

    List<RetailProduct> findByIsDeletedFalseOrderByNameAsc();

    // 需求（追加）：成本回填清單——找出還沒設定進貨成本（unitCost=0）的上架商品，
    // 給後台專屬的「待補成本」畫面用，不用讓店家從一長串商品清單裡自己大海撈針找。
    // 注意：0 元本來就是合法的成本值（例如贈品類商品），這裡沒辦法百分之百排除
    // 「刻意設成 0」跟「還沒填」的差別，只能當作篩選起點，實際是否要補由店家自行判斷。
    List<RetailProduct> findByIsDeletedFalseAndUnitCostOrderByNameAsc(int unitCost);

    // 需求 7-2 之後會用來查低庫存商品，先建起來
    List<RetailProduct> findByIsDeletedFalseAndStockQuantityLessThan(int threshold);
}
