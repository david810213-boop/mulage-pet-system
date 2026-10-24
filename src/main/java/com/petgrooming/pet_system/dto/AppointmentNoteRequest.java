package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 店家設定預約備注（雙可見性）
 * internalNote 僅店家可見；memberNote 會員可見
 * 兩者皆選填，只更新有帶的欄位
 */
@Data
public class AppointmentNoteRequest {
    @Schema(description = "店家內部備注（例：耳朵加強去油、造型需求）")
    private String internalNote;
    @Schema(description = "會員可見備注")
    private String memberNote;
}
