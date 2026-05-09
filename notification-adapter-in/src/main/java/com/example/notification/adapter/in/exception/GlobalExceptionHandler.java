package com.example.notification.adapter.in.exception;

import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.exception.RecipientNotFoundException;
import com.example.notification.application.exception.TemplateNotFoundException;
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
