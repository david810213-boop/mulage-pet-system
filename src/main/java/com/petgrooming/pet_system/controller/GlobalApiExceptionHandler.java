package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.ErrorResponse;
import com.petgrooming.pet_system.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 全站 API（/api/**、@RestController）共用的例外處理。
 *
 * 背景：@Valid 驗證失敗（例如必填欄位沒填）預設會被 Spring 轉成
 * RFC 7807 的 ProblemDetail 格式回應，這個格式沒有 "message" 欄位，
 * 前端 `data?.message || data || res.status` 這種寫法抓不到訊息，
 * 退回去把整個物件塞進字串，畫面就會顯示「儲存失敗：[object Object]」。
 *
 * 這裡統一攔截，改成回傳 { "message": "<第一個欄位的錯誤訊息>" }，
 * 跟系統其他地方的回應格式一致，前端不用另外為這種例外寫特殊處理。
 *
 * 需求（追加，2026-09-13）：業務邏輯的「這個操作不合法」錯誤，過去是每支
 * REST controller 各自寫 try/catch(IllegalArgumentException) 再組
 * ResponseEntity.badRequest()。清查後發現 13 個 REST controller 裡總共
 * 33 處幾乎一模一樣的樣板，而且回傳格式並不一致：29 處直接回傳
 * `e.getMessage()`（純字串，不是物件），只有 4 處包成
 * `{"message": ...}`——前端幾乎所有地方讀的都是 `data.message`，這代表
 * 那 29 處的錯誤訊息前端其實抓不到，只是剛好都有 fallback 文字頂著沒被
 * 發現（跟手動綁定功能踩過的「找不到符合的資料」是同一種問題模式）。
 * 這裡統一攔截 IllegalArgumentException / IllegalStateException，全部
 * 回傳同一種 `{"message": ...}` 格式，13 個 controller 裡對應的
 * try/catch 都已經拿掉，直接讓例外往外丟給這裡接。
 *
 * 另外攔截 DataIntegrityViolationException（資料庫外鍵/唯一鍵約束衝突）
 * ——這類例外過去完全沒有全域處理，會整包 SQL 例外堆疊直接曝露給前端、
 * 回傳 500（例如手動綁定功能刪除來源帳號撞到 wallets 外鍵那次事故）。
 * 這裡攔截後回傳一個對使用者友善、不洩漏內部細節的通用訊息，狀態碼用
 * 409 Conflict（比 500 更準確：這是資料狀態衝突，不是伺服器壞掉）。
 *
 * 需求（追加，2026-09-13）：回應內容改用 {@link ErrorResponse} 這個專門的
 * DTO，取代原本的 Map.of("message", ...)。理由：Map 沒有型別檢查，欄位
 * 名稱是字串，容易打錯（手動綁定功能就曾經誤用過 "error" 而不是
 * "message"，前端讀不到，直到後來才發現）。改成 DTO 之後欄位名稱由編譯器
 * 保證正確，序列化出來的 JSON 格式完全不變（一樣是 {"message": "..."}），
 * 前端不用改。
 */
@Slf4j
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("輸入資料格式不正確");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(message));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusinessError(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ex.getMessage()));
    }

    // 需求（追加，2026-09-13）：新的分業務例外類別（見 exception 套件），全部
    // 繼承 BusinessException，這裡攔截一次，所有子類別（MemberException /
    // PetException / WalletException / AuthException 等）都會自動被接住，
    // 不用每個子類別各寫一支 handler。狀態碼採用例外自己帶的 status（例如
    // AuthException 預設 401，其餘預設 400），跟上面泛用例外的處理方式不同，
    // 但回傳格式一致，前端不用區分是哪一種例外。
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleDomainBusinessError(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("⚠️ [全域例外處理] 資料庫約束衝突，回傳友善訊息給前端：{}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("這筆資料跟其他紀錄有關聯，無法執行這個操作，請聯絡工程人員協助確認"));
    }
}
