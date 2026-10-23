package com.petgrooming.pet_system.exception;

/**
 * 圖片/檔案上傳相關業務錯誤：Cloudinary 圖床設定、檔案格式驗證、
 * 上傳失敗等。用在 CloudinaryService，被寵物照片、美容狀況紀錄照片
 * 等多個地方共用。
 */
public class MediaException extends BusinessException {
    public MediaException(String message) {
        super(message);
    }

    public MediaException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
