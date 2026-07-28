package com.petgrooming.pet_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.petgrooming.pet_system.model.GroomingItem;

@Repository
public interface GroomingItemRepository extends JpaRepository<GroomingItem, Long> {

    // 查詢所有未被邏輯刪除的服務項目
    List<GroomingItem> findByIsDeletedFalse();

    // 需求 4：查詢可線上預約的項目（bookable=true 且未下架）— 供 LIFF 預約頁使用
    List<GroomingItem> findByBookableTrueAndIsDeletedFalse();

    // 根據 itemCode 查詢服務項目（用於結帳時驗證）
    Optional<GroomingItem> findByItemCodeAndIsDeletedFalse(String itemCode);

    // 查出整筆美容服務
    Optional<GroomingItem> findByItemCode(String itemCode);
    
    // 檢查代碼是否已存在
    boolean existsByItemCode(String itemCode);}

