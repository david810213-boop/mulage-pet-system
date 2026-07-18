package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CustomerSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

// 會員編輯自己的基本資料（年齡／職業／居住區域／得知來源），皆為選填
@Data
public class UpdateProfileRequest {

    private String name; // 允許順便修改顯示名稱（選填，不填則不更動）

    @Min(value = 0, message = "年齡不可小於 0")
    @Max(value = 150, message = "年齡數值不正確")
    private Integer age;

    private String occupation;

    private String residenceArea;

    private CustomerSource source;
}
