package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 顧客問慕馬的一則訊息。 */
public record AssistantMessageRequest(
        @Schema(description = "提問內容")
        @NotBlank(message = "請輸入問題")
        @Size(max = 200, message = "問題請在 200 字以內")
        String message,

        @Schema(description = "顧客目前所在的 LIFF 頁面，例如 index、booking")
        @Size(max = 40)
        String page) {
}
