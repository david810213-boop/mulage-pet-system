package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;

/** 店家手動將匯入的會員資料合併到既有帳號。 */
public record MemberMergeRequest(
        @NotBlank(message = "請選擇要合併的匯入資料") String importedUsername,
        @NotBlank(message = "請選擇要合併到的會員帳號") String targetUsername) {
}
