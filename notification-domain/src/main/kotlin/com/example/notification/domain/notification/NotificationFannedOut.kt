package com.example.notification.domain.notification

import com.example.notification.domain.shared.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 알림이 채널별로 fan-out 되어 발송 attempt 가 만들어졌음을 외부에 알리는 이벤트.
 *
 * Outbox → Kafka topic `notification.fanned-out` 로 발행. 각 channel worker 가 이
 * 이벤트의 attempt id 만 보고 자기 채널의 row 를 가져가 vendor 호출.
 *
 * `@JvmRecord` 의 record-style accessor (`eventId()` / `occurredAt()`) 가 [DomainEvent]
 * 인터페이스를 구현 — JSON 직렬화 필드명도 record component 이름과 동일하게 유지됨.
 */
@JvmRecord
data class NotificationFannedOut(
    override val eventId: String,
    override val occurredAt: Instant,
    val notificationId: UUID,
    val deliveryAttemptIds: List<UUID>,
) : DomainEvent {

    companion object {
        @JvmStatic
        fun of(notificationId: UUID, attemptIds: List<UUID>): NotificationFannedOut =
            NotificationFannedOut(
                UUID.randomUUID().toString(),
                Instant.now(),
                notificationId,
                java.util.List.copyOf(attemptIds),
            )
    }
}
