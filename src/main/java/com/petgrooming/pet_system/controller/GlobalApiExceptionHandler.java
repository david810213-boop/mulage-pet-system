package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.exception.BusinessException;
import com.petgrooming.pet_system.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * 全站共用的例外處理：業務例外、請求驗證失敗、資料衝突。回應格式見 {@link ApiProblems}。
 *
 * 適用範圍是所有 controller（包含網頁版回傳 JSON 的端點），因此這裡只處理「已知」的例外；
 * 未預期的例外與 Spring 內建錯誤由 {@link RestApiFallbackExceptionHandler} 處理，且只限 REST controller，
 * 避免網頁畫面的錯誤也被轉成 JSON。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalApiExceptionHandler {

    static final String GENERIC_BAD_REQUEST = "請求內容不正確，請確認後再試一次";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        return ApiProblems.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        String message = fieldErrors.stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("輸入資料格式不正確");
        List<Map<String, String>> errors = fieldErrors.stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return ApiProblems.validation(message, errors);
    }

    /**
     * 非業務例外的 IllegalArgumentException／IllegalStateException（例如函式庫、列舉轉換丟出的），
     * 訊息可能含內部細節，不直接回給使用者。
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ProblemDetail> handleIllegalArgument(RuntimeException ex) {
        log.warn("[API 例外] 非業務例外的參數錯誤：{}", ex.toString());
        return ApiProblems.of(ErrorCode.MALFORMED_REQUEST, GENERIC_BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("[API 例外] 資料庫約束衝突：{}", ex.getMostSpecificCause().getMessage());
        return ApiProblems.of(ErrorCode.CONFLICT, "這筆資料跟其他紀錄有關聯，無法執行這個操作，請聯絡工程人員協助確認");
    }
}
