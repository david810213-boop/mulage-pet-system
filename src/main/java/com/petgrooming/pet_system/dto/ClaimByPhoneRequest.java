package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;

/** 認領既有會員資料：電話，以及毛孩名字（沒有毛孩則為登記姓名）作為身分確認。 */
public record ClaimByPhoneRequest(
        @NotBlank(message = "請輸入電話號碼") String phone,
        String verification) {
}
