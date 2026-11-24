package com.petgrooming.pet_system.dto;

/** 管理後台美容項目分類按鈕：分類代碼、名稱、項目數，dog 為 true 時顯示體型篩選。 */
public record GroomingCategoryView(String key, String label, long count, boolean dog) {
}
