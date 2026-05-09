package com.example.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "delivery_attempt",
        indexes = {
            @Index(name = "ix_delivery_attempt_notification", columnList = "notification_id"),
            @Index(name = "ix_delivery_attempt_status_next", columnList = "status,next_attempt_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttemptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    private com.example.notification.domain.channel.ChannelType channelType;

    @Column(name = "channel_address", nullable = false, length = 256)
    private String channelAddress;

    @Column(name = "rendered_title", nullable = false, length = 200)
    private String renderedTitle;

    @Column(name = "rendered_body", nullable = false, length = 4000)
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private com.example.notification.domain.delivery.DeliveryStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "vendor_message_id", length = 128)
    private String vendorMessageId;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
