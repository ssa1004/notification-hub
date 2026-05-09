package com.example.notification.domain.delivery;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 한 채널에 대한 발송 요청 이벤트. Outbox → Kafka topic {@code notification.delivery.<channel>}
 * 로 발행. 채널별 worker (FCM consumer / SES consumer 등) 가 이를 consume.
 *
 * <p>topic 분리 이유: 채널별 처리량 / 가격 / SLA 가 달라 partition / consumer-group / dead
 * letter 정책을 분리하기 쉬움. ADR-0002 참조.
 */
public record DeliveryRequested(
        String eventId,
        Instant occurredAt,
        UUID notificationId,
        UUID deliveryAttemptId,
        ChannelType channelType)
        implements DomainEvent {

    public static DeliveryRequested of(
            UUID notificationId, UUID deliveryAttemptId, ChannelType channelType) {
        return new DeliveryRequested(
                UUID.randomUUID().toString(),
                Instant.now(),
                notificationId,
                deliveryAttemptId,
                channelType);
    }
}
