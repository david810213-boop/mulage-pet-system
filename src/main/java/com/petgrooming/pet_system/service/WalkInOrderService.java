package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.GroomingItemComponentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderItemRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 需求 5：現場開單——建立、結帳前編輯項目、查詢。
 *
 * 結束服務與核對見 {@link WalkInOrderCheckService}；
 * 結帳／匯款確認／退款與金額計算見 {@link WalkInPaymentService}；
 * 經手人與積分（跟預約結帳走同一套績效表，算進員工月結算）見 {@link WalkInOrderOperatorService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkInOrderService {

    private final WalkInOrderRepository orderRepository;
    private final WalkInOrderItemRepository orderItemRepository;
    private final WalkInPaymentService walkInPaymentService; // 結帳與消費明細折扣標示
    private final WalkInOrderOperatorService walkInOrderOperatorService; // 經手人與積分
    private final GroomingItemRepository groomingItemRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 需求（追加）：僅限既有客戶項目判斷
    private final GroomingItemComponentRepository groomingItemComponentRepository; // 需求（追加）：套餐組成
    private final PetRepository petRepository; // 需求（追加）：查現場單寵物物種
    private final RetailProductService retailProductService; // 需求 7-1：零售商品加購

    // ── 建立現場單 ───────────────────────────────────────────────────────
    /**
     * 建立現場單。可以只有零售商品（純零售訂單）。
     * 開單當下就指定經手人的項目立即計分；開單人得到「接進」積分（現場單沒有獨立的開始服務步驟）。
     */
    @Transactional
    public WalkInOrderResponse create(WalkInOrderCreateRequest req, String createdByUsername) {
        boolean hasServiceItems = req.getItems() != null && !req.getItems().isEmpty();
        boolean hasRetailItems = req.getRetailItems() != null && !req.getRetailItems().isEmpty();
        if (!hasServiceItems && !hasRetailItems) {
            throw new PaymentException("請至少加入一個美容項目或零售商品");
        }
        User creator = userRepository.findByUsername(createdByUsername).orElse(null);
        WalkInOrder order = newOrder(req, creator);
        if (hasServiceItems) {
            req.getItems().forEach(line -> addServiceItem(order, line, req.getPetName()));
        }
        if (hasRetailItems) {
            req.getRetailItems().forEach(line -> addRetailItems(order, line));
        }
        order.setTotalAmount(order.getItems().stream().mapToInt(WalkInOrderItem::getPrice).sum());

        WalkInOrder saved = orderRepository.save(order);
        saved.getItems().stream()
                .filter(item -> item.getOperatorStaff() != null)
                .forEach(item -> walkInOrderOperatorService.awardPoints(item, saved));
        log.info("現場開單 #{} 完成，共 {} 項，總額 {}", saved.getId(), saved.getItems().size(), saved.getTotalAmount());
        if (creator != null) {
            performanceService.addWalkInRecord(creator.getId(), saved.getId(),
                    PerformanceCategory.CHECKIN, PerformanceCategory.CHECKIN.getDefaultPoints(),
                    saved.getCreatedAt().toLocalDate(), "接待入場：現場單 #" + saved.getId());
        }
        return WalkInOrderResponse.from(saved);
    }

    /** 開單人姓名存快照、帳號供接進積分歸屬；會員為選填，有填就必須存在。 */
    private WalkInOrder newOrder(WalkInOrderCreateRequest req, User creator) {
        WalkInOrder order = WalkInOrder.builder()
                .petName(req.getPetName())
                .note(req.getNote())
                .build();
        if (creator != null) {
            order.setCreatedBy(creator.getName());
            order.setCreatedByStaff(creator);
        }
        if (req.getMemberUsername() != null && !req.getMemberUsername().isBlank()) {
            order.setMember(userRepository.findByUsername(req.getMemberUsername())
                    .orElseThrow(() -> new PaymentException("找不到會員：" + req.getMemberUsername())));
        }
        return order;
    }

    /**
     * 加入一個服務項目（名稱、價格、積分存快照）。套餐的副組成展開成價格 0 的明細列，
     * 只供經手人分別計分；主項目名稱此時加上積分分類標籤，避免店家誤以為主分類的積分不見了。
     */
    private void addServiceItem(WalkInOrder order, WalkInOrderCreateRequest.Item line, String petName) {
        GroomingItem gi = groomingItemRepository.findByItemCode(line.getItemCode())
                .orElseThrow(() -> new PaymentException("找不到項目代碼：" + line.getItemCode()));
        requireExistingCustomerIfRestricted(gi, order.getMember(), petName);
        var components = groomingItemComponentRepository.findByGroomingItemId(gi.getId());

        WalkInOrderItem item = WalkInOrderItem.builder()
                .groomingItemId(gi.getId())
                .itemName(components.isEmpty() ? gi.getName() : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）")
                .price((int) Math.round(gi.getPrice()))
                .points(gi.getPoints())
                .performanceCategory(gi.getPerformanceCategory())
                .discountEligible(gi.isDiscountEligible())
                .build();
        walkInOrderOperatorService.assignOperators(item, line.getOperatorStaffId(), line.getOperatorStaffId2());
        order.addItem(item);

        for (var component : components) {
            order.addItem(WalkInOrderItem.builder()
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName() + "（" + component.getPerformanceCategory().getLabel() + "）")
                    .price(0)
                    .points(component.getPoints())
                    .performanceCategory(component.getPerformanceCategory())
                    .discountEligible(false)
                    .build());
        }
    }

    /** 僅限既有客戶的項目：沒綁會員就無法查消費紀錄，保守擋下。 */
    private void requireExistingCustomerIfRestricted(GroomingItem gi, User member, String petName) {
        if (!gi.isRequiresExistingCustomer()) {
            return;
        }
        boolean existingCustomer = member != null
                && petConsumptionHistoryService.hasPriorPaidService(member.getId(), petName, null);
        if (!existingCustomer) {
            throw new PaymentException("「" + gi.getName() + "」僅限既有客戶，無法加入此項目");
        }
    }

    /** 零售商品依數量逐件建立明細列；數量 0 以下的略過。 */
    private void addRetailItems(WalkInOrder order, WalkInOrderCreateRequest.RetailItem line) {
        if (line.getQuantity() <= 0) {
            return;
        }
        var product = retailProductService.getById(line.getRetailProductId());
        for (int i = 0; i < line.getQuantity(); i++) {
            order.addItem(buildRetailOrderItem(product));
        }
    }

    // ── 需求 7-1：現場單加購零售商品 ─────────────────────────────────────
    // 結帳前都可以加購（不受「結束服務/核對」流程限制，因為零售商品不是美容服務，
    // 跟原本的服務項目走的是不同的完成判定邏輯）。quantity 幾件就建幾筆項目列，
    // 沿用既有 WalkInOrderItem 的「一列 = 一份，price 是單價」慣例，
    // 不另外加 quantity 欄位去動到既有折扣/合計計算邏輯，降低牽連風險。
    @Transactional
    public WalkInOrderResponse addRetailItem(Long orderId, Long retailProductId, int quantity, String username) {
        if (quantity <= 0) throw new PaymentException("加購數量必須大於 0");

        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.isPaid()) {
            throw new PaymentException("此單已結帳，無法再加購商品");
        }

        var product = retailProductService.getById(retailProductId);

        int addedTotal = 0;
        for (int i = 0; i < quantity; i++) {
            order.addItem(buildRetailOrderItem(product));
            addedTotal += product.getPrice();
        }
        order.setTotalAmount(order.getTotalAmount() + addedTotal);
        WalkInOrder saved = orderRepository.save(order);

        log.info("現場單 #{} 加購商品「{}」x{}", orderId, product.getName(), quantity);
        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        walkInPaymentService.populateDiscountInfo(res, saved);
        return res;
    }

    // 需求 7-1：建立一筆零售商品的訂單項目快照，供開單當下加購、結帳頁加購共用同一份邏輯，
    // 避免兩處各寫一次、日後改欄位漏改其中一邊。
    private WalkInOrderItem buildRetailOrderItem(RetailProduct product) {
        return WalkInOrderItem.builder()
                .retailProductId(product.getId())
                .itemName(product.getName())
                .price(product.getPrice())
                .points(0.0)
                .performanceCategory(PerformanceCategory.OTHER)
                .discountEligible(false) // 零售商品不參與會員折扣／回洗優惠，維持原價
                .build();
    }

    // ── 需求（追加）：這隻寵物是不是既有客戶（供畫面過濾「僅限既有客戶」項目用）───
    // 現場單沒綁會員的話沒辦法查歷史消費紀錄，保守回傳 false（視為不是既有客戶）。
    public boolean isExistingCustomerPet(Long orderId) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.getMember() == null) return false;
        return petConsumptionHistoryService.hasPriorPaidService(
                order.getMember().getId(), order.getPetName(), orderId);
    }

    // 需求（追加）：這張現場單的寵物種類（供畫面過濾「適用物種」項目用）；
    // 沒綁會員或查不到對應的寵物資料就回傳 null（無法判斷，畫面上不濾掉任何物種，避免誤擋）。
    public String getPetTypeForOrder(Long orderId) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.getMember() == null) return null;
        return petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .map(pet -> pet.getPetType().name())
                .orElse(null);
    }

    // 需求（追加，2026-08-24）：狗狗定價流程簡化——這張現場單對應的完整寵物資料
    // （含目前體重、鎖定的固定套餐），供結帳頁篩選「新增服務項目」下拉選單、
    // 顯示鎖定套餐提示、還有「鎖定為固定套餐」按鈕使用。
    // 沒綁會員或查不到對應寵物就回傳 null（純現場客、沒建檔的寵物，不套用這套邏輯）。
    public PetResponse getPetForOrder(Long orderId) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.getMember() == null) return null;
        var pet = petRepository.findByOwnerUsernameAndName(order.getMember().getUsername(), order.getPetName())
                .orElse(null);
        if (pet == null) return null;
        var lockedItem = pet.getLockedGroomingItemId() != null
                ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                : null;
        return PetResponse.from(pet, lockedItem);
    }

    // 需求（追加）：編輯訂單——結帳前補一筆漏開/開錯的美容服務項目
    // 需求（追加，2026-08-26）：customPrice 選填，店員手動輸入的自訂價格
    // （例如剪毛這種價格浮動的項目）。null 或負數就照項目原本的固定價格；
    // 有給值就用那個值當這筆的實際收費。⚠️ 積分固定照 gi.getPoints()
    // 這個項目本身設定的值計算，跟改過的價格完全無關——這是刻意設計，
    // 店員改價格是為了反映「這次實際收多少錢」，不代表這個服務項目本身
    // 的積分認定跟著變動，避免同一個服務項目因為每次報價不同、績效認列
    // 卻忽高忽低。
    @Transactional
    public WalkInOrderResponse addGroomingItem(Long orderId, Long groomingItemId, String username) {
        return addGroomingItem(orderId, groomingItemId, null, username);
    }

    @Transactional
    public WalkInOrderResponse addGroomingItem(Long orderId, Long groomingItemId, Integer customPrice, String username) {
        return addGroomingItem(orderId, groomingItemId, customPrice, null, null, username);
    }

    @Transactional
    public WalkInOrderResponse addGroomingItem(Long orderId, Long groomingItemId, Integer customPrice,
            Long operatorStaffId, String username) {
        return addGroomingItem(orderId, groomingItemId, customPrice, operatorStaffId, null, username);
    }

    // 需求（追加，2026-10-02 店家確認 3-1）：核對時加的項目也可以當場選經手人（null＝稍後補填）
    @Transactional
    public WalkInOrderResponse addGroomingItem(Long orderId, Long groomingItemId, Integer customPrice,
            Long operatorStaffId, Long operatorStaffId2, String username) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.isPaid()) {
            throw new PaymentException("此單已結帳，無法再編輯項目，請改用退款重開");
        }

        GroomingItem gi = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到服務項目"));

        // 需求（追加）：僅限既有客戶的項目，沒綁會員的現場單無法查歷史消費紀錄，
        // 保守起見一律擋下（不確定是不是既有客戶時，不開放使用這類項目）。
        if (gi.isRequiresExistingCustomer()) {
            boolean isExisting = order.getMember() != null
                    && petConsumptionHistoryService.hasPriorPaidService(
                            order.getMember().getId(), order.getPetName(), orderId);
            if (!isExisting) {
                throw new PaymentException("「" + gi.getName() + "」僅限既有客戶，無法加入此項目");
            }
        }

        // 需求（追加）：主項目本身掛的積分分類如果有副組成，名稱後面比照副組成
        // 加上分類標籤（見上方建單流程同樣的說明）。有自訂價格的話，名稱後面
        // 也標一下「（自訂價格）」，方便日後對帳時一眼看出這筆不是原價。
        var itemComponents = groomingItemComponentRepository.findByGroomingItemId(gi.getId());
        boolean hasCustomPrice = customPrice != null && customPrice >= 0;
        String mainItemName = (itemComponents.isEmpty() ? gi.getName()
                : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）")
                + (hasCustomPrice ? "（自訂價格）" : "");
        int actualPrice = hasCustomPrice ? customPrice : (int) Math.round(gi.getPrice());

        WalkInOrderItem item = WalkInOrderItem.builder()
                .groomingItemId(gi.getId())
                .itemName(mainItemName)
                .price(actualPrice)
                .points(gi.getPoints()) // 積分固定照項目原本設定，不受自訂價格影響
                .performanceCategory(gi.getPerformanceCategory())
                .discountEligible(gi.isDiscountEligible())
                .build();
        walkInOrderOperatorService.assignOperators(item, operatorStaffId, operatorStaffId2);
        order.addItem(item);
        order.setTotalAmount(order.getTotalAmount() + item.getPrice());

        // 需求（追加）：套餐化——展開副組成
        for (var component : itemComponents) {
            WalkInOrderItem sub = WalkInOrderItem.builder()
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName() + "（" + component.getPerformanceCategory().getLabel() + "）")
                    .price(0)
                    .points(component.getPoints())
                    .performanceCategory(component.getPerformanceCategory())
                    .discountEligible(false)
                    .build();
            order.addItem(sub);
        }
        WalkInOrder saved = orderRepository.save(order);
        walkInOrderOperatorService.awardNewlyAssignedItems(saved);

        log.info("現場單 #{} 編輯新增服務項目「{}」{}", orderId, gi.getName(),
                hasCustomPrice ? "（自訂價格 $" + actualPrice + "）" : "");
        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        walkInPaymentService.populateDiscountInfo(res, saved);
        return res;
    }

    // ── 需求（追加，2026-08-26）：自訂金額加購 ──────────────────────────────
    // 用途：處理「高階定制調理」（開放式報價）跟各種浮動加價（厚毛/長毛/
    // 特殊剪法/特殊情況）——這些沒辦法用固定價目表項目涵蓋，店員需要現場
    // 依實際情況打一個自訂名稱+金額的項目進去，不綁定任何現有的 GroomingItem。
    // 積分分類由店員自己選，積分固定套用那個分類的預設積分；不參與任何折扣
    // （discountEligible 固定 false），因為這種客製化報價本來就是店員當下
    // 依實際情況談定的金額，不應該再疊加折扣。
    @Transactional
    public WalkInOrderResponse addCustomItem(Long orderId, String itemName, int price,
            PerformanceCategory category, String username) {
        return addCustomItem(orderId, itemName, price, category, null, null, username);
    }

    @Transactional
    public WalkInOrderResponse addCustomItem(Long orderId, String itemName, int price,
            PerformanceCategory category, Long operatorStaffId, String username) {
        return addCustomItem(orderId, itemName, price, category, operatorStaffId, null, username);
    }

    @Transactional
    public WalkInOrderResponse addCustomItem(Long orderId, String itemName, int price,
            PerformanceCategory category, Long operatorStaffId,
            Long operatorStaffId2, String username) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.isPaid()) {
            throw new PaymentException("此單已結帳，無法再編輯項目，請改用退款重開");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new PaymentException("請填寫項目名稱");
        }
        if (price < 0) {
            throw new PaymentException("金額不能是負數");
        }

        var actualCategory = category != null ? category : PerformanceCategory.OTHER;
        WalkInOrderItem item = WalkInOrderItem.builder()
                .groomingItemId(null) // 不綁定任何現有服務項目
                .itemName(itemName.trim() + "（自訂項目）")
                .price(price)
                .points(actualCategory.getDefaultPoints())
                .performanceCategory(actualCategory)
                .discountEligible(false)
                .build();
        walkInOrderOperatorService.assignOperators(item, operatorStaffId, operatorStaffId2);
        order.addItem(item);
        order.setTotalAmount(order.getTotalAmount() + price);
        WalkInOrder saved = orderRepository.save(order);
        walkInOrderOperatorService.awardNewlyAssignedItems(saved);

        log.info("現場單 #{} 新增自訂項目「{}」，金額 ${}", orderId, itemName, price);
        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        walkInPaymentService.populateDiscountInfo(res, saved);
        return res;
    }

    // 移除一筆項目（結帳前都可以移除，不管是零售商品還是美容服務項目；
    // 結帳後只能整筆退款重開，這裡的「已結帳」防呆維持不變）。
    @Transactional
    public WalkInOrderResponse removeItem(Long orderId, Long orderItemId, String username) {
        WalkInOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        if (order.isPaid()) {
            throw new PaymentException("此單已結帳，無法再編輯項目，請改用退款重開");
        }

        WalkInOrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到項目 #" + orderItemId));
        if (!item.getOrder().getId().equals(orderId)) {
            throw new PaymentException("項目不屬於這張現場單");
        }
        if (order.getItems().size() <= 1) {
            throw new PaymentException("這是最後一筆項目，無法移除；如果整張單都要取消，請改用退款流程");
        }

        order.setTotalAmount(order.getTotalAmount() - item.getPrice());
        order.getItems().remove(item); // orphanRemoval = true，從集合移除就會自動連帶刪除
        WalkInOrder saved = orderRepository.save(order);

        WalkInOrderResponse res = WalkInOrderResponse.from(saved);
        walkInPaymentService.populateDiscountInfo(res, saved);
        return res;
    }

    // ── 所有現場單（交易紀錄列表）───────────────────────────────────────────
    public List<WalkInOrderResponse> listAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(WalkInOrderResponse::from).toList();
    }

    // ── 查單筆現場單完整明細（供會員信息頁「消費記錄」點擊查看用）─────────
    public WalkInOrderResponse getById(Long id) {
        WalkInOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new PaymentException(ErrorCode.NOT_FOUND, "找不到現場單 #" + id));
        WalkInOrderResponse res = WalkInOrderResponse.from(order);
        walkInPaymentService.populateDiscountInfo(res, order);
        return res;
    }

    /** 全部現場單（實體），新的在前；報表與營收計算用。 */
    public List<WalkInOrder> findAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<WalkInOrder> findOrdersOfMember(String username) {
        return orderRepository.findByMemberUsernameOrderByCreatedAtDesc(username);
    }
}
