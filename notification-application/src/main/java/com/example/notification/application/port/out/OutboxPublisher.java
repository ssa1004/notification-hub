package com.example.notification.application.port.out;

import com.example.notification.domain.shared.DomainEvent;

/**
 * Outbox 패턴의 내부 port — use case 가 도메인 이벤트를 발행할 때 호출.
 *
 * <p>구현체는 Kafka 가 아닌 DB outbox 테이블에 적재. 실제 Kafka 발행은 별도 OutboxRelay 가
 * polling 으로 처리 (DB commit ↔ Kafka send 원자성 보장 — ADR-0004).
 *
 * <p>그러므로 use case 입장에서는 동기 호출처럼 보이지만 실제로는 트랜잭션 commit 시점에
 * outbox 에 기록되고 Kafka 는 별도 워커가 보냅니다. 이 함수에서 IOException 발생하지 않음.
 */
public interface OutboxPublisher {

    /**
     * 이벤트를 outbox 에 적재.
     *
     * @param topic Kafka topic 이름 (relay 가 사용)
     * @param key partition key (예: notificationId — 같은 알림은 순서 보존)
     * @param event 직렬화 대상 도메인 이벤트
     */
    void publish(String topic, String key, DomainEvent event);
}
