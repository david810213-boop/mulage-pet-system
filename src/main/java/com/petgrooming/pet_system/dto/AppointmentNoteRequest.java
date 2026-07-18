package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 需求 7：店家設定預約備注（雙可見性）
 * internalNote 僅店家可見；memberNote 會員可見
 * 兩者皆選填，只更新有帶的欄位
 */
@Data
public class AppointmentNoteRequest {
    private String internalNote; // 店家內部備注（例：耳朵加強去油、造型需求）
    private String memberNote;   // 會員可見備注
}
