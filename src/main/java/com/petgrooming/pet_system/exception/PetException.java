package com.petgrooming.pet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * 寵物相關業務錯誤：新增/編輯/刪除寵物、鎖定與解鎖固定套餐、
 * 毛長/毛髮分類設定等。用在 PetService。
 */
public class PetException extends BusinessException {
    public PetException(String message) {
        super(message);
    }

    public PetException(String message, HttpStatus status) {
        super(message, status);
    }
}
