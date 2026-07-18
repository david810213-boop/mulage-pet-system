package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ADMIN 重設員工密碼用（不需輸入舊密碼，ADMIN 有權限直接指定新密碼）
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密碼不能為空")
    @Size(min = 4, message = "新密碼至少需要 4 個字元")
    private String newPassword;
}
