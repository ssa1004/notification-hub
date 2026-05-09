package com.example.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbox row. 도메인 트랜잭션과 같은 트랜잭션 안에서 INSERT 됩니다. {@code OutboxRelay} 가
 * polling 으로 PENDING row 를 가져가 Kafka 로 publish 후 PUBLISHED 마킹.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = {
            @Index(name = "ix_outbox_event_status_created", columnList = "status,created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "key_value", nullable = false, length = 128)
    private String keyValue;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_json", nullable = false, length = 4000)
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
