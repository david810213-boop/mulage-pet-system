package com.petgrooming.pet_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.petgrooming.pet_system.enums.PetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class PetRequest {

    @NotBlank(message = "毛孩名字不能為空")
    private String name;

    @NotNull(message = "毛孩類型不能為空")
    @Schema(description = "DOG / CAT / OTHER")
    private PetType petType;

    @NotBlank(message = "品種不能為空")
    private String breed;

    @NotNull(message = "體重不能為空")
    @Positive(message = "體重必須大於 0")
    private Double weight;

    @NotNull(message = "年齡不能為空")
    @PositiveOrZero(message = "年齡不能為負數")
    @Schema(description = "允許小數，例如 5.5 歲，方便標記幼犬/幼貓的半歲")
    private Double age;

    // 毛長不再由顧客填寫（改由店家後台定義），故此處移除 coatType 欄位。
    // 前端即使誤傳此欄位，也會被忽略（Spring Boot 預設不因未知屬性報錯）。

    @Schema(description = "是否有分離焦慮")
    private Boolean hasSeparationAnxiety = false;

    @Schema(description = "家長手機")
    private String ownerPhone;

    @Schema(description = "注意事項")
    private String notes;
    // 體型（sizeCategory）不由前端傳入，系統依 petType + weight 自動判斷

    // ── 定型化契約要求蒐集的資料（皆選填）───────────────────────
    private String gender;
    private Boolean isNeutered;
    private Boolean hasChip;
    private String chipNumber;
    private String personalityTags;
    private String healthHistory;
    private String healthHistoryOther;
    private Boolean hasDesignatedVet;
    private String designatedVetName;
    private String designatedVetAddress;
    private String designatedVetPhone;
}
