package com.example.notification.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * Outbox row. 도메인 트랜잭션과 같은 트랜잭션 안에서 INSERT 됩니다. `OutboxRelay` 가
 * polling 으로 PENDING row 를 가져가 Kafka 로 publish 후 PUBLISHED 마킹.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes = [
        Index(name = "ix_outbox_event_status_created", columnList = "status,created_at"),
    ],
)
class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "topic", nullable = false, length = 128)
    var topic: String = ""

    @Column(name = "key_value", nullable = false, length = 128)
    var keyValue: String = ""

    @Column(name = "event_id", nullable = false, length = 64)
    var eventId: String = ""

    @Column(name = "event_type", nullable = false, length = 64)
    var eventType: String = ""

    @Column(name = "payload_json", nullable = false, length = 4000)
    var payloadJson: String = ""

    @Column(name = "status", nullable = false, length = 16)
    var status: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
