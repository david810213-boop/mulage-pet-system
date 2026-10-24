package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentItemResponse;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.AppointmentException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem; // ⚡ 確保引入的是你動態管理的 Entity 類別
import com.petgrooming.pet_system.model.GroomingItemComponent;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemComponentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository; // ⚡ 注入 Repository 來查資料庫
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 預約的訂單項目（到店開單後的服務項目、自訂項目、零售商品加購）：結帳前編輯、經手人與積分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentItemService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final GroomingItemRepository groomingItemRepository;
    private final GroomingItemComponentRepository groomingItemComponentRepository; // 套餐組成
    private final UserRepository userRepository;
    private final PerformanceService performanceService;
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 僅限既有客戶項目判斷
    private final RetailProductService retailProductService; // 結帳頁加購零售商品

    // ── 需求（追加）：預約結帳頁加購零售商品 ────────────────────────────
    // 結帳前都可以加購（不受核對/checkinOrderConfirmed 限制，零售商品不是美容服務，
    // 走的是不同的完成判定邏輯）；quantity 幾件就建幾筆項目列，沿用 WalkInOrderItem
    // 「一列 = 一份，price 是單價」的慣例，不加 quantity 欄位，降低牽連風險。
    @Transactional
    public void addRetailItem(Long appointmentId, Long retailProductId, int quantity, String username) {
        if (quantity <= 0) throw new AppointmentException("加購數量必須大於 0");

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (appointment.isPaid()) {
            throw new AppointmentException("此預約已結帳，無法再加購商品");
        }

        var product = retailProductService.getById(retailProductId);

        int addedTotal = 0;
        for (int i = 0; i < quantity; i++) {
            AppointmentItem item = AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .retailProductId(product.getId())
                    .itemName(product.getName())
                    .price(product.getPrice())
                    .points(0.0)
                    .performanceCategory(PerformanceCategory.OTHER)
                    .build();
            appointmentItemRepository.save(item);
            addedTotal += product.getPrice();
        }
        appointment.setTotalAmount(appointment.getTotalAmount() + addedTotal);
        appointmentRepository.save(appointment);

        log.info("預約 #{} 加購商品「{}」x{}", appointmentId, product.getName(), quantity);
    }

    // ── 需求（追加）：編輯訂單——結帳前新增一筆美容服務項目 ────────────────
    // 跟加購零售商品同一套「結帳前才准動」的限制；核對時發現漏開/開錯項目，
    // 不用整筆退款重開，直接在這裡補上即可。
    @Transactional
    // 需求（追加，2026-08-26）：customPrice 選填，跟 WalkInOrderService 同一套
    // 設計，積分固定照 gi.getPoints() 計算，跟改過的價格完全無關。
    public void addGroomingItem(Long appointmentId, Long groomingItemId, String username) {
        addGroomingItem(appointmentId, groomingItemId, null, username);
    }

    public void addGroomingItem(Long appointmentId, Long groomingItemId, Integer customPrice, String username) {
        addGroomingItem(appointmentId, groomingItemId, customPrice, null, null, username);
    }

    @Transactional
    public void addGroomingItem(Long appointmentId, Long groomingItemId, Integer customPrice,
            Long operatorStaffId, String username) {
        addGroomingItem(appointmentId, groomingItemId, customPrice, operatorStaffId, null, username);
    }

    // 需求（追加，2026-10-02 店家確認 3-1）：核對時加的項目也可以當場選經手人
    @Transactional
    public void addGroomingItem(Long appointmentId, Long groomingItemId, Integer customPrice,
            Long operatorStaffId, Long operatorStaffId2, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (appointment.isPaid()) {
            throw new AppointmentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }

        GroomingItem gi = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到服務項目"));

        // 需求（追加）：僅限既有客戶的項目（例如貓咪基礎保養），這隻寵物完全沒有
        // 消費紀錄的話不能加入這個項目。
        if (gi.isRequiresExistingCustomer()
                && !petConsumptionHistoryService.hasPriorPaidService(
                        appointment.getUser().getId(), appointment.getPetName(), appointmentId)) {
            throw new AppointmentException("「" + gi.getName() + "」僅限既有客戶，這隻寵物還沒有消費紀錄，無法加入此項目");
        }

        // 需求（追加）：主項目本身掛的積分分類如果有副組成，名稱後面比照副組成
        // 加上分類標籤（見上方 checkin 流程同樣的說明）。有自訂價格的話，名稱
        // 後面也標一下「（自訂價格）」，方便日後對帳時一眼看出這筆不是原價。
        var itemComponents = groomingItemComponentRepository.findByGroomingItemId(gi.getId());
        boolean hasCustomPrice = customPrice != null && customPrice >= 0;
        String mainItemName = (itemComponents.isEmpty() ? gi.getName()
                : gi.getName() + "（" + gi.getPerformanceCategory().getLabel() + "）")
                + (hasCustomPrice ? "（自訂價格）" : "");
        int actualPrice = hasCustomPrice ? customPrice : (int) Math.round(gi.getPrice());

        AppointmentItem item = AppointmentItem
                .builder()
                .appointment(appointment)
                .groomingItemId(gi.getId())
                .itemName(mainItemName)
                .price(actualPrice)
                .points(gi.getPoints()) // 積分固定照項目原本設定，不受自訂價格影響
                .performanceCategory(gi.getPerformanceCategory())
                .build();
        assignOperators(item, operatorStaffId, operatorStaffId2);
        appointmentItemRepository.save(item);
        awardItemPoints(item);
        expandPackageComponents(appointment, gi, itemComponents); // 需求（追加）：套餐化——展開副組成

        appointment.setTotalAmount(appointment.getTotalAmount() + item.getPrice());
        appointment.setCheckinOrderConfirmed(true); // 保險起見一併標記，避免極少數尚未開過單的情況卡在後續流程
        appointmentRepository.save(appointment);

        log.info("預約 #{} 編輯新增服務項目「{}」", appointmentId, gi.getName());
    }

    // ── 需求（追加，2026-08-26）：自訂金額加購 ──────────────────────────────
    // 用途：處理「高階定制調理」（開放式報價）跟各種浮動加價（厚毛/長毛/
    // 特殊剪法/特殊情況）——這些沒辦法用固定價目表項目涵蓋，店員需要現場
    // 依實際情況打一個自訂名稱+金額的項目進去，不綁定任何現有的 GroomingItem。
    // 積分分類由店員自己選（下拉選單挑一個既有的績效分類），積分固定套用
    // 那個分類的預設積分（跟 GroomingItem 新增時「沒填積分就用分類預設值」
    // 同一套邏輯）；不參與任何折扣（discountEligible 固定 false），因為這種
    // 客製化報價本來就是店員當下依實際情況談定的金額，不應該再疊加折扣。
    public void addCustomItem(Long appointmentId, String itemName, int price,
            PerformanceCategory category, String username) {
        addCustomItem(appointmentId, itemName, price, category, null, null, username);
    }

    @Transactional
    public void addCustomItem(Long appointmentId, String itemName, int price,
            PerformanceCategory category, Long operatorStaffId, String username) {
        addCustomItem(appointmentId, itemName, price, category, operatorStaffId, null, username);
    }

    @Transactional
    public void addCustomItem(Long appointmentId, String itemName, int price,
            PerformanceCategory category, Long operatorStaffId,
            Long operatorStaffId2, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (appointment.isPaid()) {
            throw new AppointmentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new AppointmentException("請填寫項目名稱");
        }
        if (price < 0) {
            throw new AppointmentException("金額不能是負數");
        }

        var actualCategory = category != null ? category : PerformanceCategory.OTHER;
        AppointmentItem item = AppointmentItem
                .builder()
                .appointment(appointment)
                .groomingItemId(null) // 不綁定任何現有服務項目
                .itemName(itemName.trim() + "（自訂項目）")
                .price(price)
                .points(actualCategory.getDefaultPoints())
                .performanceCategory(actualCategory)
                .build();
        assignOperators(item, operatorStaffId, operatorStaffId2);
        appointmentItemRepository.save(item);
        awardItemPoints(item);

        appointment.setTotalAmount(appointment.getTotalAmount() + price);
        appointment.setCheckinOrderConfirmed(true);
        appointmentRepository.save(appointment);

        log.info("預約 #{} 新增自訂項目「{}」，金額 ${}", appointmentId, itemName, price);
    }

    // 需求（追加）：套餐化——一個套餐項目結帳/開單時，除了自己的主組成（已經記錄在
    // AppointmentItem 本身），如果後台有設定「副組成」，這裡一併展開成額外的待補經手人
    // 紀錄（price 固定 0，不重複計價，純粹是為了矩陣待補經手人表單能拆出每個積分分類，
    // 可能由不同美容師分開處理）。
    // components 參數由呼叫端先查好傳進來，避免跟「要不要加主項目標籤」那次查詢重複查兩次。
    void expandPackageComponents(Appointment appointment, GroomingItem gi,
            List<GroomingItemComponent> components) {
        for (var component : components) {
            AppointmentItem sub = AppointmentItem
                    .builder()
                    .appointment(appointment)
                    .groomingItemId(gi.getId())
                    .itemName(gi.getName() + "（" + component.getPerformanceCategory().getLabel() + "）")
                    .price(0)
                    .points(component.getPoints())
                    .performanceCategory(component.getPerformanceCategory())
                    .build();
            appointmentItemRepository.save(sub);
        }
    }

    // 移除一筆項目（結帳前都可以移除，不管是零售商品還是美容服務項目；
    // 結帳後只能整筆退款重開，這裡的「已結帳」防呆維持不變）。
    @Transactional
    public void removeItem(Long appointmentId, Long itemId, String username) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到該預約"));
        if (appointment.isPaid()) {
            throw new AppointmentException("此預約已結帳，無法再編輯項目，請改用退款重開");
        }

        AppointmentItem item = appointmentItemRepository.findById(itemId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到項目 #" + itemId));
        if (!item.getAppointment().getId().equals(appointmentId)) {
            throw new AppointmentException("項目不屬於這筆預約");
        }
        if (appointmentItemRepository.findByAppointmentId(appointmentId).size() <= 1) {
            throw new AppointmentException("這是最後一筆項目，無法移除；如果整筆都要取消，請改用退款流程");
        }

        appointment.setTotalAmount(appointment.getTotalAmount() - item.getPrice());
        appointmentItemRepository.delete(item);
        appointmentRepository.save(appointment);
    }

    // ── 待補經手人清單（現場開單項目中 operatorStaff 為 null 的）─────────────
    // 需求（追加）：只列出「有積分可算」的項目——零售商品加購（points 固定 0）
    // 不會產生績效，補了經手人也沒有計算意義，不需要出現在這份待辦清單裡。
    public List<AppointmentItemResponse> pendingItemOperators() {
        return appointmentItemRepository.findByOperatorStaffIsNull().stream()
                .filter(item -> item.getPoints() > 0)
                .map(AppointmentItemResponse::from)
                .toList();
    }

    // ── 補填某個現場開單項目的經手人（同步寫入績效紀錄）─────────────────────
    @Transactional
    public void fillItemOperator(Long appointmentItemId, Long staffId) {
        fillItemOperator(appointmentItemId, staffId, null);
    }

    // 需求（2026-10-02 店家確認 5）：待補經手人也可以一次選兩位
    @Transactional
    public void fillItemOperator(Long appointmentItemId, Long staffId, Long staffId2) {
        AppointmentItem item = appointmentItemRepository.findById(appointmentItemId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到項目 #" + appointmentItemId));

        if (item.getOperatorStaff() != null) {
            throw new AppointmentException("此項目已填寫經手人，無法重複填寫");
        }

        assignOperators(item, staffId, staffId2);
        appointmentItemRepository.save(item);

        awardItemPoints(item);
    }

    // 需求（2026-10-02 店家確認 5）：設定經手人（＋選填的第二經手人）
    // 規則：第一位沒選時不能選第二位；兩位不能是同一人。
    void assignOperators(AppointmentItem item, Long id1, Long id2) {
        if (id1 == null && id2 != null) {
            throw new AppointmentException("請先選第一位經手人，再選第二經手人");
        }
        if (id1 != null && id1.equals(id2)) {
            throw new AppointmentException("兩位經手人不能是同一個人");
        }
        item.setOperatorStaff(findOperator(id1));
        item.setOperatorStaff2(findOperator(id2));
    }

    // 經手人 id → 員工（null＝稍後補填）；只接受店員／管理者帳號
    private User findOperator(Long staffId) {
        if (staffId == null) return null;
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new AppointmentException(ErrorCode.NOT_FOUND, "找不到員工 #" + staffId));
        if (!staff.isStaffOrAdmin()) {
            throw new AppointmentException("經手人必須是店內員工");
        }
        return staff;
    }

    // ── 現場開單項目積分寫入：一筆項目只會被計入一次（pointsAwarded 防重複）──
    public void awardItemPoints(AppointmentItem item) {
        if (item.isPointsAwarded())
            return;
        if (item.getOperatorStaff() == null)
            return;
        if (item.getPerformanceCategory() == PerformanceCategory.OTHER)
            return;
        if (item.getPoints() <= 0)
            return;

        Appointment appt = item.getAppointment();
        if (item.getOperatorStaff2() != null) {
            // 需求（2026-10-02）：雙人經手，積分各半
            performanceService.addDualRecords(
                    item.getOperatorStaff().getId(), item.getOperatorStaff2().getId(),
                    appt.getId(), null,
                    item.getPerformanceCategory(), item.getPoints(), appt.getDate(),
                    "預約 #" + appt.getId() + " - " + item.getItemName());
        } else {
            performanceService.addRecord(
                    item.getOperatorStaff().getId(),
                    appt.getId(),
                    item.getPerformanceCategory(),
                    item.getPoints(),
                    appt.getDate(),
                    "預約 #" + appt.getId() + " - " + item.getItemName());
        }

        item.setPointsAwarded(true);
        appointmentItemRepository.save(item);
    }

    // ── 結帳時呼叫：把此預約現場開單項目中「已有經手人」的積分一次結算 ──────
    // 沒有經手人的項目會跳過，之後從「補項目經手人」清單補上時再另外觸發積分。
    public boolean hasCheckinOrderItems(Long appointmentId) {
        return !appointmentItemRepository.findByAppointmentId(appointmentId).isEmpty();
    }

    @Transactional
    public void awardPendingItemPointsForAppointment(Long appointmentId) {
        List<AppointmentItem> items = appointmentItemRepository
                .findByAppointmentId(appointmentId);
        for (AppointmentItem item : items) {
            awardItemPoints(item);
        }
    }

    // ── 取得某預約的現場開單項目明細（供進行中核對頁面顯示給家長核對用）─────
    public List<AppointmentItem> getCheckinItems(Long appointmentId) {
        return appointmentItemRepository.findByAppointmentId(appointmentId);
    }
}
