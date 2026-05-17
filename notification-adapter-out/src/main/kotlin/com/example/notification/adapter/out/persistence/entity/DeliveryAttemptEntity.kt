package com.example.notification.adapter.out.persistence.entity

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "delivery_attempt",
    indexes = [
        Index(name = "ix_delivery_attempt_notification", columnList = "notification_id"),
        Index(name = "ix_delivery_attempt_status_next", columnList = "status,next_attempt_at"),
    ],
)
class DeliveryAttemptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID(0, 0)

    @Column(name = "notification_id", nullable = false)
    var notificationId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    var channelType: ChannelType = ChannelType.PUSH

    @Column(name = "channel_address", nullable = false, length = 256)
    var channelAddress: String = ""

    @Column(name = "rendered_title", nullable = false, length = 200)
    var renderedTitle: String = ""

    @Column(name = "rendered_body", nullable = false, length = 4000)
    var renderedBody: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: DeliveryStatus = DeliveryStatus.PENDING

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "vendor_message_id", length = 128)
    var vendorMessageId: String? = null

    @Column(name = "failure_reason", length = 512)
    var failureReason: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH
}
