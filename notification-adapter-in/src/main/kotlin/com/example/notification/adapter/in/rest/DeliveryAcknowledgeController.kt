package com.example.notification.adapter.`in`.rest

import com.example.notification.adapter.`in`.rest.dto.AcknowledgeDeliveryRequest
import com.example.notification.adapter.`in`.security.HmacSignatureVerifier
import com.example.notification.adapter.`in`.security.WebhookSecrets
import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.time.Clock
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * vendor 측 webhook 수신용 endpoint.
 *
 * **왜 서명 검증**: 누군가 콜백 URL 만 알면 가짜 "전송 성공" 을 박을 수 있고, 사용자에게는
 * 알림이 안 갔는데 시스템은 성공으로 마킹되는 사고. [HmacSignatureVerifier] 가
 * vendor 별 secret + timestamp 윈도우 (5분) 로 차단.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
class DeliveryAcknowledgeController(
    private val ackUseCase: AcknowledgeDeliveryUseCase,
    private val webhookSecrets: WebhookSecrets,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{deliveryAttemptId}/ack")
    fun acknowledge(
        @PathVariable("deliveryAttemptId") deliveryAttemptId: UUID,
        @RequestHeader(value = "X-Notification-Hub-Vendor", required = false) vendor: String?,
        @RequestHeader(value = "X-Notification-Hub-Signature", required = false) signature: String?,
        @RequestHeader(value = "X-Notification-Hub-Timestamp", required = false) timestamp: String?,
        httpRequest: HttpServletRequest,
        @Valid @RequestBody request: AcknowledgeDeliveryRequest,
    ): ResponseEntity<Void> {
        // 가짜 콜백 차단 — vendor 식별 + secret 매핑 + HMAC 검증.
        // fail-closed — vendor 등록 안 됨 / secret 미설정 / 서명 불일치 모두 거절.
        val secret = webhookSecrets.secretFor(vendor)
        if (secret == null) {
            log.warn("webhook 거절: vendor 미등록 또는 secret 미설정 vendor={}", vendor)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown vendor or secret")
        }
        // body 를 다시 직렬화해 HMAC 입력으로 사용. 운영에서는 ContentCachingRequestWrapper 로
        // raw bytes 를 그대로 사용하는 편이 더 정확 — 본 ADR 의 단순화.
        val body = objectMapper.writeValueAsBytes(request)
        val ok = HmacSignatureVerifier.verify(
            secret, signature, timestamp, body, clock.millis(),
        )
        if (!ok) {
            log.warn("webhook 서명 검증 실패 vendor={} attemptId={}", vendor, deliveryAttemptId)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid signature")
        }
        ackUseCase.acknowledge(request.toCommand(deliveryAttemptId))
        return ResponseEntity.accepted().build()
    }
}
