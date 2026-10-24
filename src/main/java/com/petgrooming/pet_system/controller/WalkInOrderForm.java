package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 現場開單表單的服務項目解析（網頁版與手機版開單表單同樣格式）。
 *
 * 表單送出 itemCodes、operatorStaffIds、operatorStaffIds2 三個平行陣列，順序一一對應；
 * 項目代碼空白的列（表單裡沒選的空列）跳過，經手人空白代表稍後再從待補清單補填。
 */
final class WalkInOrderForm {

    private WalkInOrderForm() {
    }

    static List<WalkInOrderCreateRequest.Item> serviceItems(List<String> itemCodes,
                                                            List<String> operatorStaffIds,
                                                            List<String> operatorStaffIds2) {
        List<WalkInOrderCreateRequest.Item> items = new ArrayList<>();
        if (itemCodes == null) return items;
        for (int i = 0; i < itemCodes.size(); i++) {
            String code = itemCodes.get(i);
            if (code == null || code.isBlank()) continue;
            WalkInOrderCreateRequest.Item item = new WalkInOrderCreateRequest.Item();
            item.setItemCode(code);
            item.setOperatorStaffId(staffIdAt(operatorStaffIds, i));
            item.setOperatorStaffId2(staffIdAt(operatorStaffIds2, i));
            items.add(item);
        }
        return items;
    }

    private static Long staffIdAt(List<String> staffIds, int index) {
        String value = staffIds != null && index < staffIds.size() ? staffIds.get(index) : null;
        return value != null && !value.isBlank() ? Long.valueOf(value) : null;
    }
}
