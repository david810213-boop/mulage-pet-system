package com.petgrooming.pet_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/**
 * 回傳可預約時段給前端
 * 需求 3：同時段最多 5 隻，改為回傳已預約數 / 上限 / 剩餘名額，
 * 前端可顯示「剩 N 位」而非只有可 / 不可。
 */
@Data
@AllArgsConstructor
public class TimeSlotResponse {
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;   // remaining > 0（且，需求追加 2026-09-08：查詢時有帶物種的話，也已排除物種不符）
    private int booked;          // 已預約數
    private int capacity;        // 上限（5）
    private int remaining;       // 剩餘名額
    // 需求（追加，2026-09-08）：這個時段限定的物種（"DOG"/"CAT"/null＝不限制），
    // 不論查詢時有沒有帶 petType 都會回傳，讓前端可以標示「🐕限定/🐱限定」提示，
    // 不只是單純把限制反映在 available 上。
    private String allowedPetType;

    // 相容舊建構子（若他處僅用 3 參數建立）
    public TimeSlotResponse(LocalTime startTime, LocalTime endTime, boolean available) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.available = available;
        this.capacity = 5;
        this.booked = available ? 0 : 5;
        this.remaining = available ? 5 : 0;
        this.allowedPetType = null;
    }
}
