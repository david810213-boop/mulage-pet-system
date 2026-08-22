package com.petgrooming.pet_system.service; 

import java.util.List;
import java.util.stream.Collectors; 

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petgrooming.pet_system.dto.GroomingItemRequest;  
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.UpdateGroomingItemRequest;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.service.interfaces.GroomingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroomingServiceImpl implements GroomingService {

    private final GroomingItemRepository groomingItemRepository;

    /**
     * 新增美容服務項目
     */
    @Override
    @Transactional
    public void createItem(GroomingItemRequest request) {
        // 1. 防重機制：檢查 ItemCode（如 GS001）是否已經存在於資料庫
        if (groomingItemRepository.existsByItemCode(request.getItemCode())) {
            throw new IllegalArgumentException("建立失敗：項目代碼 [" + request.getItemCode() + "] 已存在！");
        }

        // 2. 建立全新的 Entity 並將 DTO 的資料填入
        GroomingItem item = new GroomingItem();
        item.setItemCode(request.getItemCode());
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setDeleted(false); // 新增的項目預設就是直接上架使用
        item.setBookable(request.getBookable() != null && request.getBookable()); // 需求 4
        item.setDiscountEligible(request.getDiscountEligible() == null || request.getDiscountEligible()); // 需求（追加）

        // 需求（追加）：分類決定積分（每個分類都有預設積分，不用手動輸入），也決定
        // 這個項目會不會被回洗優惠/首次體驗優惠這類「依分類判斷」的邏輯認得到。
        var category = request.getPerformanceCategory() != null
                ? request.getPerformanceCategory()
                : com.petgrooming.pet_system.enums.PerformanceCategory.OTHER;
        item.setPerformanceCategory(category);
        // 需求（追加）：積分分類——店家有指定就用指定值，沒指定才退回分類預設值（維持舊行為）
        item.setPoints(request.getPoints() != null ? request.getPoints() : category.getDefaultPoints());
        item.setRequiresExistingCustomer(request.getRequiresExistingCustomer() != null && request.getRequiresExistingCustomer());
        item.setApplicablePetType(request.getApplicablePetType());

        // 3. 實質寫入資料庫
        groomingItemRepository.save(item);
    }

    /**
     * 1. 撈出整個美容項目菜單 (只拿沒被下架的)
     * 需求：CHECKIN/CHECKOUT/COMPLETE 這三個項目是初始化時建立的內部流程標記
     * （接待入場/接待送出/完成確認，$0 元，積分邏輯直接用 PerformanceCategory 計算，
     * 不會真的去查這三個項目），不是給客人選的真實服務，一律從價目表/選單中排除。
     */
    @Override
    @Transactional(readOnly = true) 
    public List<GroomingItemResponse> getAllItems() {
        List<GroomingItem> items = groomingItemRepository.findByIsDeletedFalse();
        return items.stream()
                .filter(GroomingServiceImpl::isCustomerFacingItem)
                .map(GroomingItemResponse::from)
                .collect(Collectors.toList());
    }

    // 排除接待流程用的內部標記項目（CHECKIN / CHECKOUT / COMPLETE）
    private static boolean isCustomerFacingItem(GroomingItem item) {
        var category = item.getPerformanceCategory();
        return category != com.petgrooming.pet_system.enums.PerformanceCategory.CHECKIN
                && category != com.petgrooming.pet_system.enums.PerformanceCategory.CHECKOUT
                && category != com.petgrooming.pet_system.enums.PerformanceCategory.COMPLETE;
    }

    /**
     * 1b. 需求 4：只撈可線上預約的項目（大美容 / 小美容 / 精緻洗 / 定製洗）
     */
    @Override
    @Transactional(readOnly = true)
    public List<GroomingItemResponse> getBookableItems() {
        return groomingItemRepository.findByBookableTrueAndIsDeletedFalse().stream()
                .map(GroomingItemResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 2. 需求 14：修改指定的美容項目（名稱/描述/金額/是否可預約）——不含代碼，代碼不可改
     */
    @Override
    @Transactional
    public GroomingItemResponse updateItem(Long id, UpdateGroomingItemRequest request) {
        GroomingItem item = groomingItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("修改失敗：找不到 ID 為 " + id + " 的美容項目"));

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        if (request.getBookable() != null) item.setBookable(request.getBookable()); // 需求 4
        if (request.getPoints() != null) item.setPoints(request.getPoints()); // 需求（追加）：積分分類可個別調整
        if (request.getRequiresExistingCustomer() != null) item.setRequiresExistingCustomer(request.getRequiresExistingCustomer());
        if (request.getApplicablePetType() != null) item.setApplicablePetType(request.getApplicablePetType());
        
        GroomingItem updatedItem = groomingItemRepository.save(item);
        return GroomingItemResponse.from(updatedItem);
    }

    /**
     * 3. 刪除指定的美容項目 (實作「邏輯刪除」下架)
     */
    @Override
    @Transactional
    public void deleteItem(Long id) {
        GroomingItem item = groomingItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("刪除失敗：找不到 ID 為 " + id + " 的美容項目"));

        item.setDeleted(true); 
        groomingItemRepository.save(item);
    }
}