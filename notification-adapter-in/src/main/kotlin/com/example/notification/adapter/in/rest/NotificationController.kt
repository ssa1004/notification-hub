package com.example.notification.adapter.`in`.rest

import com.example.notification.adapter.`in`.rest.dto.SendNotificationRequest
import com.example.notification.adapter.`in`.rest.dto.SendNotificationResponse
import com.example.notification.application.dto.DeliveryHistoryPage
import com.example.notification.application.port.`in`.ListMyDeliveriesUseCase
import com.example.notification.application.port.`in`.SendNotificationUseCase
import com.example.notification.domain.notification.NotificationStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val sendUseCase: SendNotificationUseCase,
    private val listUseCase: ListMyDeliveriesUseCase,
) {

    /**
     * 알림 전송. `Idempotency-Key` 헤더 필수 — 같은 키 재요청은 409.
     *
     * 응답 코드:
     * - 202 Accepted — fan-out 됨 (vendor 호출은 비동기)
     * - 200 OK + status=SUPPRESSED — opt-out / DND 로 발송 안 함
     * - 409 — 멱등성 키 중복
     * - 404 — recipient 없음
     * - 429 — rate limit 초과
     */
    @PostMapping
    fun send(
        @RequestHeader("Idempotency-Key") @NotBlank idempotencyKey: String,
        @Valid @RequestBody request: SendNotificationRequest,
    ): ResponseEntity<SendNotificationResponse> {
        val result = sendUseCase.send(request.toCommand(idempotencyKey))
        val status = if (result.status == NotificationStatus.SUPPRESSED) {
            HttpStatus.OK
        } else {
            HttpStatus.ACCEPTED
        }
        return ResponseEntity.status(status).body(SendNotificationResponse.from(result))
    }

    /** 내 알림 이력. cursor 페이지네이션. */
    @GetMapping("/me")
    fun list(
        @RequestParam("recipientId") @NotBlank recipientId: String,
        @RequestParam(value = "cursor", required = false) cursor: UUID?,
        @RequestParam(value = "limit", defaultValue = "20") limit: Int,
    ): DeliveryHistoryPage = listUseCase.list(recipientId, cursor, limit)

    /** 단건 조회 — operational debug. */
    @GetMapping("/{id}")
    fun get(@PathVariable("id") id: UUID): ResponseEntity<UUID> {
        // 단순화: id 조회는 별도 use case 없이 응답만 echo. 실제론 NotificationDetailUseCase 추가.
        return ResponseEntity.ok(id)
    }
}
