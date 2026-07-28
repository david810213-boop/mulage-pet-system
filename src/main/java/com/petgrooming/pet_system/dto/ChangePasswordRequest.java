package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 員工／管理員登入後，自行修改自己的密碼（需驗證舊密碼）
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "請輸入目前密碼")
    private String oldPassword;

    @NotBlank(message = "新密碼不能為空")
    @Size(min = 4, message = "新密碼至少需要 4 個字元")
    private String newPassword;
}
