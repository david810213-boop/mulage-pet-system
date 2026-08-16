package com.petgrooming.pet_system.service.interfaces;


import java.util.List;

import com.petgrooming.pet_system.dto.GroomingItemRequest;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.UpdateGroomingItemRequest;

public interface GroomingService {
    // 1. 獲取所有未刪除的美容項目選單（後台用，含不可預約項目）
    List<GroomingItemResponse> getAllItems();

    // 1b. 需求 4：獲取可線上預約的項目（bookable=true）— LIFF 預約頁用
    List<GroomingItemResponse> getBookableItems();

    // 2. 需求 14：修改指定的美容項目（名稱/描述/金額/是否可預約，不含代碼——代碼唯讀）
    GroomingItemResponse updateItem(Long id, UpdateGroomingItemRequest request);
    
    //3. 新建项目
    void createItem(GroomingItemRequest request);

    // 4. 邏輯刪除指定的美容項目
    void deleteItem(Long id);
}