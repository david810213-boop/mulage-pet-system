package com.petgrooming.pet_system.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全站 API（/api/**、@RestController）共用的例外處理。
 *
 * 背景：@Valid 驗證失敗（例如必填欄位沒填）預設會被 Spring 轉成
 * RFC 7807 的 ProblemDetail 格式回應，這個格式沒有 "message" 欄位，
 * 前端 `data?.message || data || res.status` 這種寫法抓不到訊息，
 * 退回去把整個物件塞進字串，畫面就會顯示「儲存失敗：[object Object]」。
 *
 * 這裡統一攔截，改成回傳 { "message": "<第一個欄位的錯誤訊息>" }，
 * 跟系統其他地方（例如手動 catch IllegalArgumentException 後
 * ResponseEntity.badRequest().body(Map.of("message", ...))）的回應格式一致，
 * 前端不用另外為這種例外寫特殊處理。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("輸入資料格式不正確");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
