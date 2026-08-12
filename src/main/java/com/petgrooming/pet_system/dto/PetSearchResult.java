package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 需求 9：以寵物名稱搜尋時的結果，自動帶出所屬家長資訊。
 * 若有同名寵物，displayLabel 會附上家長姓名＋電話末四碼方便店員辨識。
 */
@Data
@Builder
public class PetSearchResult {
    private Long petId;
    private String petName;
    private String petType;
    private String breed;
    private String ownerUsername;
    private String ownerName;
    private String ownerPhone;
    private String displayLabel; // 例如：「小白（家長：陳小明・電話末四碼 1234）」
}
