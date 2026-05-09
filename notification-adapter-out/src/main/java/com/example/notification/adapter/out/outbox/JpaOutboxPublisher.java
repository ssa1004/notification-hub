package com.example.notification.adapter.out.outbox;

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity;
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.domain.shared.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OutboxPublisher 구현. 도메인 트랜잭션 안에서 outbox 테이블에 INSERT 만 합니다.
 * 실제 Kafka 발행은 {@link OutboxRelay} 가 별도 polling 으로 처리.
 *
 * <p>{@link DomainEvent} → JSON 직렬화는 Jackson. record 타입이라 추가 설정 불필요.
 */
@Component
@RequiredArgsConstructor
public class JpaOutboxPublisher implements OutboxPublisher {

    static final String STATUS_PENDING = "PENDING";

    private final OutboxEventJpaRepository jpa;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, String key, DomainEvent event) {
        OutboxEventEntity row = new OutboxEventEntity();
        row.setTopic(topic);
        row.setKeyValue(key);
        row.setEventId(event.eventId());
        row.setEventType(event.getClass().getSimpleName());
        row.setPayloadJson(serialize(event));
        row.setStatus(STATUS_PENDING);
        row.setCreatedAt(Instant.now());
        jpa.save(row);
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("event serialize failed: " + event, e);
        }
    }
}
