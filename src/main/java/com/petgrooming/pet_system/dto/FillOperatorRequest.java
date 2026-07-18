package com.petgrooming.pet_system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 需求 6：補填某個現場單項目的經手人（綁定實際員工帳號）
 */
@Data
public class FillOperatorRequest {
    @NotNull(message = "請選擇經手員工")
    private Long staffId;
}
