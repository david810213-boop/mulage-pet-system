package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.exception.ErrorCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * 統一的 API 錯誤回應（RFC 9457 Problem Details）：
 * <pre>
 * { "type": "about:blank", "title": "Not Found", "status": 404, "detail": "找不到該預約",
 *   "instance": "/api/appointments/9", "code": "NOT_FOUND", "message": "找不到該預約" }
 * </pre>
 * {@code message} 與 {@code detail} 相同，保留給既有前端（一律讀 message）；
 * 新的前端程式請依 {@code code} 判斷錯誤類型。驗證失敗時另有 {@code errors} 列出每個欄位。
 */
final class ApiProblems {

    private ApiProblems() {
    }

    static ResponseEntity<ProblemDetail> of(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(problem(code, message));
    }

    static ResponseEntity<ProblemDetail> validation(String message, List<Map<String, String>> fieldErrors) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, message);
        body.setProperty("errors", fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    static ProblemDetail problem(ErrorCode code, String message) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), message);
        enrich(body, code, message);
        return body;
    }

    static void enrich(ProblemDetail body, ErrorCode code, String message) {
        body.setDetail(message);
        body.setProperty("code", code.name());
        body.setProperty("message", message);
    }
}
