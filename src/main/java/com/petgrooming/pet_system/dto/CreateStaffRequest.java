package com.petgrooming.pet_system.dto;

import lombok.Data;

// ADMIN 建立員工帳號
@Data
public class CreateStaffRequest {
    private String username;    // email
    private String password;
    private String name;       
}
