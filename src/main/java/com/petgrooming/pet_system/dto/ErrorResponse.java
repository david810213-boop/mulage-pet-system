package com.petgrooming.pet_system.dto;

import lombok.Data;

/**
 * 需求（追加，2026-09-13）：全站 API 錯誤回應的統一格式。
 *
 * 背景：GlobalApiExceptionHandler 原本用 Map.of("message", ...) 組回應，
 * 沒有型別檢查，容易打錯 key（例如手動綁定功能一開始誤用 "error" 而不是
 * "message"，前端讀不到，直到後來才發現）。改成專門的 DTO 之後，欄位名稱
 * 由編譯器保證正確，且 IDE 打字時會自動提示，不會再打錯字。
 *
 * 目前只有 message 這一個欄位，刻意保持跟現有前端契約（幾乎所有地方都是
 * 讀 `data.message`）完全一致，不多加欄位——這次是把既有回應格式「型別化」，
 * 不是重新設計錯誤回應格式，避免動到前端。
 */
@Data
public class ErrorResponse {
    private String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message);
    }
}
