package com.example.notification.domain.shared;

import java.time.Instant;

/**
 * 도메인이 외부에 알리는 사건. Outbox 에 적재되어 Kafka 로 발행됩니다.
 *
 * <p>구현체는 record 권장 — 불변 + equals/hashCode 자동 + 직렬화 안전.
 */
public interface DomainEvent {

    /** 이벤트의 전역 고유 id. consumer 측 dedup 키로 사용됩니다. */
    String eventId();

    /** 이벤트 발생 시각. */
    Instant occurredAt();
}
