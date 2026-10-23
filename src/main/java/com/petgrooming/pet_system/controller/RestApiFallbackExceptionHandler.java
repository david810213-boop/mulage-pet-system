package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.exception.ErrorCode;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * REST controller 專用的後備例外處理（只套用在 @RestController，不影響網頁畫面）：
 * <ul>
 *   <li>Spring 內建錯誤（JSON 格式錯誤、參數型別錯誤、路徑不存在、HTTP 方法不支援等）
 *       沿用 Spring 判定的狀態碼，補上 code 與中文 message</li>
 *   <li>未預期的例外一律回 500 與通用訊息，完整內容只寫進 log 與 Sentry，不回給使用者</li>
 * </ul>
 * 已知的業務例外由 {@link GlobalApiExceptionHandler} 先處理（順序較前）。
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class RestApiFallbackExceptionHandler extends ResponseEntityExceptionHandler {

    static final String GENERIC_SERVER_ERROR = "系統發生錯誤，請稍後再試；若持續發生請聯絡店家";

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("[API 例外] 未預期錯誤 {} {}", request.getMethod(), request.getRequestURI(), ex);
        Sentry.captureException(ex);
        return ApiProblems.of(ErrorCode.INTERNAL_ERROR, GENERIC_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        ProblemDetail problem = body instanceof ProblemDetail existing
                ? existing
                : ProblemDetail.forStatus(statusCode);
        ApiProblems.enrich(problem, code, messageFor(code));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "找不到這個網址";
            case METHOD_NOT_ALLOWED -> "不支援這個操作方式";
            case INTERNAL_ERROR -> GENERIC_SERVER_ERROR;
            default -> GlobalApiExceptionHandler.GENERIC_BAD_REQUEST;
        };
    }
}
