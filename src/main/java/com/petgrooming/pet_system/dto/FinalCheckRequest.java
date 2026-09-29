package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 店員與家長核對「進行中」預約時提交的資料：
 * 本次毛孩美容狀況備註 + 家長現場簽名確認。
 */
@Data
public class FinalCheckRequest {

    @NotBlank(message = "請填寫本次毛孩美容狀況備註")
    private String note;

    @NotBlank(message = "請請家長於簽名板完成簽名確認")
    private String signatureData;

    // 需求（2026-09-29）：核對時拍的美容狀況照片（選填，最多 5 張）。
    // 照片已經先上傳到 Cloudinary，這裡只帶網址跟 public_id，兩個清單一一對應。
    private java.util.List<String> photoUrls;
    private java.util.List<String> photoPublicIds;
}
