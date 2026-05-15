package com.example.notification.adapter.in.exception;

import com.example.notification.application.exception.AttemptNotFoundException;
import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.exception.IllegalDlqOperationException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.exception.RecipientNotFoundException;
import com.example.notification.application.exception.TemplateNotFoundException;
import com.example.notification.application.exception.UnauthorizedAdminException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateRequestException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", ex.getMessage(), req);
    }

    @ExceptionHandler(RecipientNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRecipientNotFound(
            RecipientNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(
            TemplateNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest req) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        builder.header("Retry-After", String.valueOf(Math.max(1, ex.retryAfterMillis() / 1000)));
        return builder.body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", ex.getMessage(),
                        "path", req.getRequestURI(),
                        "retryAfterMillis", ex.retryAfterMillis()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
                MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            Exception ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req);
    }

    /**
     * 잘못된 형식의 query / path 파라미터 — 예: {@code /me?cursor=<garbage>} 처럼 UUID 자리에
     * UUID 가 아닌 값. Spring 의 타입 변환 실패라 위 handler 의 예외 목록에 안 잡혀 기본적으로
     * {@code handleUnexpected} 의 500 으로 떨어졌다. 호출자 입력 오류이므로 400 으로 명시 처리.
     * 원본 메시지에 내부 클래스명이 섞이므로 파라미터명만 노출한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return error(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "invalid value for parameter '" + ex.getName() + "'",
                req);
    }

    @ExceptionHandler(UnauthorizedAdminException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAdmin(
            UnauthorizedAdminException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_ADMIN", ex.getMessage(), req);
    }

    @ExceptionHandler(AttemptNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAttemptNotFound(
            AttemptNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalDlqOperationException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalDlqOperation(
            IllegalDlqOperationException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "ILLEGAL_DLQ_OPERATION", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error("unexpected error path={}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), req);
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "code", code,
                        "message", message == null ? "" : message,
                        "path", req.getRequestURI()));
    }
}
