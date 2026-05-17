package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * vendor webhook 콜백의 ack 페이로드. HMAC 서명 검증을 통과한 vendor 호출이라도 응답 본문 자체는
 * 신뢰하지 않고 길이 / 형태를 우리 측 스키마로 한 번 더 강제한다 (OWASP API10 — Unsafe
 * Consumption of APIs). DB 컬럼 한도와 일치시켜 둬서 vendor 가 비정상 응답을 줘도 ack 가 500 으로
 * 떨어지는 것이 아니라 400 으로 빠르게 거절된다.
 */
@JvmRecord
data class AcknowledgeDeliveryRequest(
    @field:NotNull val success: Boolean?,
    @field:Size(max = 128) val vendorMessageId: String?,
    @field:Size(max = 512) val failureReason: String?,
) {

    fun toCommand(deliveryAttemptId: UUID): AcknowledgeDeliveryUseCase.AcknowledgeCommand =
        AcknowledgeDeliveryUseCase.AcknowledgeCommand(
            deliveryAttemptId, success!!, vendorMessageId, failureReason,
        )
}
