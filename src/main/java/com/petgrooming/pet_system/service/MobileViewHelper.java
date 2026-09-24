package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentDetailResponse;
import com.petgrooming.pet_system.dto.MobileLineItem;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 員工手機版畫面共用的小工具（需求，2026-09-24 第四批）。
 * 預約、現場單兩個 controller 都會用到，集中在這裡避免各自複製一份。
 * 只做「畫面顯示用」的轉換，不含任何業務規則判斷。
 */
@Component
@RequiredArgsConstructor
public class MobileViewHelper {

    private final UserService userService;

    /** DOG / CAT → dog / cat，其餘 other，給 CSS 決定頭像顏色 */
    public String speciesKey(String petType) {
        if ("DOG".equalsIgnoreCase(petType)) {
            return "dog";
        }
        if ("CAT".equalsIgnoreCase(petType)) {
            return "cat";
        }
        return "other";
    }

    /** 頭像顯示的第一個字（支援 emoji 等代理對字元） */
    public String initial(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String t = name.trim();
        return t.substring(0, t.offsetByCodePoints(0, 1));
    }

    /** 0.9 → 9 折、0.85 → 85 折 */
    public String formatDiscount(double d) {
        int pct = (int) Math.round(d * 100);
        return (pct % 10 == 0 ? String.valueOf(pct / 10) : String.valueOf(pct)) + " 折";
    }

    /** 經手人下拉選單：員工＋管理員，只給 id、姓名 */
    public List<Map<String, Object>> staffOptions() {
        List<User> all = new ArrayList<>(userService.getAllStaffEntities());
        all.addAll(userService.getAllAdminEntities());
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName());
            list.add(m);
        }
        return list;
    }

    // ── 各種項目資料 → MobileLineItem ────────────────────────────────────

    /** 預約核對頁：現場開單項目 */
    public List<MobileLineItem> fromAppointmentItems(List<AppointmentItem> items) {
        return items.stream().map(it -> MobileLineItem.builder()
                .id(it.getId())
                .name(it.getItemName())
                .price(it.getPrice())
                .retail(it.getRetailProductId() != null)
                .note(it.getRetailProductId() != null ? "零售商品" : null)
                .build()).toList();
    }

    /** 預約結帳頁：含折扣資格的消費明細 */
    public List<MobileLineItem> fromAppointmentDetail(List<AppointmentDetailResponse.DetailItem> items) {
        return items.stream().map(it -> {
            String[] n = discountNote(it.isRetailItem(), it.isFirstVisitEligible(),
                    it.isRewashEligible(), it.isDiscountEligible());
            return MobileLineItem.builder()
                    .id(it.getItemId())
                    .name(it.getName())
                    .price(it.getPrice())
                    .retail(it.isRetailItem())
                    .note(n[0])
                    .noteOk(n[1] != null)
                    .build();
        }).toList();
    }

    /** 現場單：明細、核對、結帳都用這一份 */
    public List<MobileLineItem> fromWalkIn(List<WalkInOrderResponse.ItemLine> items, boolean withDiscountNote) {
        return items.stream().map(it -> {
            String[] n = withDiscountNote
                    ? discountNote(it.isRetailItem(), it.isFirstVisitEligible(), it.isRewashEligible(), it.isDiscountEligible())
                    : new String[] { it.isRetailItem() ? "零售商品" : null, null };
            return MobileLineItem.builder()
                    .id(it.getItemId())
                    .name(it.getItemName())
                    .price(it.getPrice())
                    .retail(it.isRetailItem())
                    .note(n[0])
                    .noteOk(n[1] != null)
                    .operator(it.getOperator())
                    .operatorFilled(it.isOperatorFilled())
                    .build();
        }).toList();
    }

    // 回傳 {說明文字, 有優惠時非 null}；判斷順序跟網頁版結帳頁消費明細一致
    private String[] discountNote(boolean retail, boolean firstVisit, boolean rewash, boolean memberEligible) {
        if (retail) {
            return new String[] { "零售商品，不打折", null };
        }
        if (firstVisit) {
            return new String[] { "首次體驗 9 折", "ok" };
        }
        if (rewash) {
            return new String[] { "回洗優惠 9 折", "ok" };
        }
        if (memberEligible) {
            return new String[] { "儲值金付款可享會員折扣", null };
        }
        return new String[] { "原價", null };
    }
}
