package com.example.notification.domain.notification;

import com.example.notification.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 알림이 채널별로 fan-out 되어 발송 attempt 가 만들어졌음을 외부에 알리는 이벤트.
 *
 * <p>Outbox → Kafka topic {@code notification.fanned-out} 로 발행. 각 channel worker 가 이
 * 이벤트의 attempt id 만 보고 자기 채널의 row 를 가져가 vendor 호출.
 */
public record NotificationFannedOut(
        String eventId,
        Instant occurredAt,
        UUID notificationId,
        List<UUID> deliveryAttemptIds)
        implements DomainEvent {

    public static NotificationFannedOut of(UUID notificationId, List<UUID> attemptIds) {
        return new NotificationFannedOut(
                UUID.randomUUID().toString(),
                Instant.now(),
                notificationId,
                List.copyOf(attemptIds));
    }
}
