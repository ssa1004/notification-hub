package com.example.notification.adapter.`in`.exception

import com.example.notification.application.exception.AttemptNotFoundException
import com.example.notification.application.exception.DuplicateRequestException
import com.example.notification.application.exception.IllegalDlqOperationException
import com.example.notification.application.exception.RateLimitExceededException
import com.example.notification.application.exception.RecipientNotFoundException
import com.example.notification.application.exception.TemplateNotFoundException
import com.example.notification.application.exception.UnauthorizedAdminException
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DuplicateRequestException::class)
    fun handleDuplicate(
        ex: DuplicateRequestException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", ex.message, req)

    @ExceptionHandler(RecipientNotFoundException::class)
    fun handleRecipientNotFound(
        ex: RecipientNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", ex.message, req)

    @ExceptionHandler(TemplateNotFoundException::class)
    fun handleTemplateNotFound(
        ex: TemplateNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", ex.message, req)

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(
        ex: RateLimitExceededException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        builder.header("Retry-After", (Math.max(1L, ex.retryAfterMillis / 1000L)).toString())
        return builder.body(
            mapOf(
                "timestamp" to Instant.now().toString(),
                "code" to "RATE_LIMIT_EXCEEDED",
                "message" to (ex.message ?: ""),
                "path" to req.requestURI,
                "retryAfterMillis" to ex.retryAfterMillis,
            ),
        )
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        MissingRequestHeaderException::class,
    )
    fun handleBadRequest(
        ex: Exception,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message, req)

    /**
     * 잘못된 형식의 query / path 파라미터 — 예: `/me?cursor=<garbage>` 처럼 UUID 자리에
     * UUID 가 아닌 값. Spring 의 타입 변환 실패라 위 handler 의 예외 목록에 안 잡혀 기본적으로
     * [handleUnexpected] 의 500 으로 떨어졌다. 호출자 입력 오류이므로 400 으로 명시 처리.
     * 원본 메시지에 내부 클래스명이 섞이므로 파라미터명만 노출한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            "invalid value for parameter '${ex.name}'",
            req,
        )

    @ExceptionHandler(UnauthorizedAdminException::class)
    fun handleUnauthorizedAdmin(
        ex: UnauthorizedAdminException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_ADMIN", ex.message, req)

    @ExceptionHandler(AttemptNotFoundException::class)
    fun handleAttemptNotFound(
        ex: AttemptNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", ex.message, req)

    @ExceptionHandler(IllegalDlqOperationException::class)
    fun handleIllegalDlqOperation(
        ex: IllegalDlqOperationException,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.CONFLICT, "ILLEGAL_DLQ_OPERATION", ex.message, req)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        log.error("unexpected error path={}", req.requestURI, ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.message, req)
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(
            mapOf(
                "timestamp" to Instant.now().toString(),
                "code" to code,
                "message" to (message ?: ""),
                "path" to req.requestURI,
            ),
        )
}
