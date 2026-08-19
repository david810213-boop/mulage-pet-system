package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CustomerSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// 會員編輯自己的基本資料（年齡／職業／居住區域／得知來源／電話），皆為選填
@Data
public class UpdateProfileRequest {

    // 需求（追加，配合定型化契約家長資料）：姓名、電話改為必填
    @NotBlank(message = "請填寫姓名")
    private String name;

    @NotBlank(message = "請填寫電話")
    @Pattern(regexp = "^[0-9+\\-() ]{0,20}$", message = "電話號碼格式不正確")
    private String phone;

    @Min(value = 0, message = "年齡不可小於 0")
    @Max(value = 150, message = "年齡數值不正確")
    private Integer age;

    private String occupation;

    private String residenceArea;

    private CustomerSource source;

    // ── 需求 19：定型化契約要求蒐集的資料 ─────────────────────────────
    // 通訊地址、緊急聯絡人姓名/電話/關係全部改為必填（配合定型化契約規定）。
    @NotBlank(message = "請填寫通訊地址")
    private String mailingAddress;

    @NotBlank(message = "請填寫緊急聯絡人姓名")
    private String emergencyContactName;

    @NotBlank(message = "請填寫緊急聯絡人電話")
    @Pattern(regexp = "^[0-9+\\-() ]{0,20}$", message = "緊急聯絡人電話格式不正確")
    private String emergencyContactPhone;

    @NotBlank(message = "請填寫緊急聯絡人關係")
    private String emergencyContactRelation;
}
