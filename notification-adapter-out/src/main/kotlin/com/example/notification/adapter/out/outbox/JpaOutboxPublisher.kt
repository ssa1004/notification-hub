package com.example.notification.adapter.out.outbox

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository
import com.example.notification.application.port.out.OutboxPublisher
import com.example.notification.domain.shared.DomainEvent
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import org.springframework.stereotype.Component

/**
 * OutboxPublisher 구현. 도메인 트랜잭션 안에서 outbox 테이블에 INSERT 만 합니다.
 * 실제 Kafka 발행은 [OutboxRelay] 가 별도 polling 으로 처리.
 *
 * [DomainEvent] → JSON 직렬화는 Jackson. record 타입이라 추가 설정 불필요.
 */
@Component
class JpaOutboxPublisher(
    private val jpa: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) : OutboxPublisher {

    override fun publish(topic: String, key: String, event: DomainEvent) {
        val row = OutboxEventEntity().apply {
            this.topic = topic
            keyValue = key
            eventId = event.eventId
            eventType = event.javaClass.simpleName
            payloadJson = serialize(event)
            status = STATUS_PENDING
            createdAt = Instant.now()
        }
        jpa.save(row)
    }

    private fun serialize(event: DomainEvent): String =
        try {
            objectMapper.writeValueAsString(event)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("event serialize failed: $event", e)
        }

    companion object {
        const val STATUS_PENDING = "PENDING"
    }
}
