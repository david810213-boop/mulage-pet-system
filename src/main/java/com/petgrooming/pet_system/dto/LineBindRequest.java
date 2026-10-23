package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;

/** 店員以 LINE 綁定帳號：LIFF 取得的 idToken 與後台產生的 6 位數驗證碼。 */
public record LineBindRequest(
        @NotBlank(message = "請提供驗證碼") String idToken,
        @NotBlank(message = "請提供驗證碼") String code) {
}
