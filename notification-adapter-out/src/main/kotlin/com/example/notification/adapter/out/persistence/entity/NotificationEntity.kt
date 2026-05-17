package com.example.notification.adapter.out.persistence.entity

import com.example.notification.domain.notification.NotificationKind
import com.example.notification.domain.notification.NotificationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Notification 영속 형태. 도메인 객체와는 mapper 로 분리.
 *
 * plugin.jpa 가 no-arg 생성자를 합성, plugin.spring 이 자동 open 처리. `var` 필드들은
 * Kotlin 컴파일러가 Java getter/setter (예: getId/setId) 를 자동 생성하므로 mapper Java
 * 코드와 호환됩니다.
 */
@Entity
@Table(name = "notification")
class NotificationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID(0, 0)

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    var idempotencyKey: String = ""

    @Column(name = "recipient_id", nullable = false, length = 128)
    var recipientId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    var kind: NotificationKind = NotificationKind.SERVICE

    @Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @Column(name = "body", nullable = false, length = 4000)
    var body: String = ""

    @Column(name = "payload_json", nullable = false, length = 4000)
    var payloadJson: String = ""

    @Column(name = "template_key", length = 128)
    var templateKey: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: NotificationStatus = NotificationStatus.ACCEPTED

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH
}
