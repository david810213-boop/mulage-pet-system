package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// ADMIN 建立員工帳號
@Data
public class CreateStaffRequest {
    @Schema(description = "email")
    private String username;
    private String password;
    private String name;       
}
