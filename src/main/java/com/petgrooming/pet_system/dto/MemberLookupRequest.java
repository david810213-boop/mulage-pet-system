package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;

/** 新客報到：以電話查詢是否有可認領的既有會員資料。電話格式在正規化後另行檢查。 */
public record MemberLookupRequest(
        @NotBlank(message = "請輸入 09 開頭的 10 碼手機號碼") String phone) {
}
