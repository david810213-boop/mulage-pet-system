package com.petgrooming.pet_system.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 需求 17/18：寵物照片上傳，使用 Cloudinary 免費額度雲端圖床。
 *
 * 為什麼選 Cloudinary 而不是直接存 Base64（像需求22乙方簽名檔那樣）：
 *   簽名檔只有一張、全店共用，Base64 存資料庫沒問題；但寵物照片是每隻寵物一張、
 *   數量會隨會員數持續成長，Base64 存資料庫會讓資料庫肥大、備份變慢，
 *   所以照片改用外部圖床，資料庫只存網址（photoUrl）。
 *
 * 使用前置準備（部署者需要做的事）：
 *   1. 到 https://cloudinary.com 免費註冊
 *   2. Dashboard 首頁可以看到 Cloud name / API Key / API Secret 三個值
 *   3. 設成環境變數 CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET
 *   4. 還沒申請好之前，這個服務會直接丟出清楚的錯誤訊息（不會讓系統啟動失敗，
 *      只有真的呼叫上傳功能時才會報錯），方便先開發其他功能。
 */
@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.configured = !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
        if (configured) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true));
        } else {
            this.cloudinary = null;
            log.warn("[Cloudinary] 尚未設定 CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET，圖片上傳功能目前無法使用");
        }
    }

    /**
     * 上傳一張圖片，回傳上傳結果（安全網址 + public_id，public_id 用於之後刪除/替換舊圖）。
     * @param file   前端上傳的圖片檔案
     * @param folder Cloudinary 資料夾名稱，用來分類（例如 "pets"、"grooming-notes"）
     */
    public UploadResult upload(MultipartFile file, String folder) {
        if (!configured) {
            throw new IllegalStateException("圖床尚未設定完成，請聯絡系統管理員設定 Cloudinary 環境變數");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("請選擇要上傳的圖片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上傳圖片檔案");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image"));
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.info("[Cloudinary] 上傳成功：{}", publicId);
            return new UploadResult(url, publicId);
        } catch (IOException e) {
            log.error("[Cloudinary] 上傳失敗", e);
            throw new IllegalStateException("圖片上傳失敗，請稍後再試：" + e.getMessage());
        }
    }

    /**
     * 刪除舊圖片（換新照片時清掉舊的，避免免費額度被用完的舊圖佔用）。
     * 刪除失敗不拋例外，只記警告——換照片這種操作，主要目的是讓新照片生效，
     * 舊圖沒刪乾淨不該讓整個操作失敗。
     */
    public void deleteQuietly(String publicId) {
        if (!configured || publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.warn("[Cloudinary] 刪除舊圖片失敗（不影響本次操作）：{}", e.getMessage());
        }
    }

    public record UploadResult(String url, String publicId) {}
}
