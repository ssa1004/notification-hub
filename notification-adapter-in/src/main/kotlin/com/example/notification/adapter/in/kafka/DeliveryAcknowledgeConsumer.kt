package com.example.notification.adapter.`in`.kafka

import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase
import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase.AcknowledgeCommand
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 별도 vendor 콜백 시스템이 있을 때, 우리 측 webhook 대신 Kafka 로 상태 변경 이벤트를
 * 넘겨받는 경로. 운영에선 vendor 별 형식이 다 달라 변환 layer 가 따로 있지만 여기선
 * 단순화된 JSON (`attemptId`, `success`, `vendorMessageId`, `reason`) 로 가정.
 */
@Component
class DeliveryAcknowledgeConsumer(
    private val ackUseCase: AcknowledgeDeliveryUseCase,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["notification.delivery.ack"],
        groupId = "\${spring.kafka.consumer.group-id:notification-hub}-ack",
    )
    fun onAck(message: String) {
        try {
            val p = objectMapper.readValue(message, AckPayload::class.java)
            ackUseCase.acknowledge(
                AcknowledgeCommand(
                    UUID.fromString(p.attemptId),
                    p.success,
                    p.vendorMessageId,
                    p.reason,
                ),
            )
        } catch (e: Exception) {
            log.warn("malformed ack payload={}", message, e)
        }
    }

    @JvmRecord
    data class AckPayload(
        val attemptId: String,
        val success: Boolean,
        val vendorMessageId: String?,
        val reason: String?,
    )
}
