package com.example.notification.adapter.out.persistence.entity

import com.example.notification.domain.device.DeviceToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "device_token",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_device_token_value", columnNames = ["token"]),
    ],
    indexes = [
        Index(name = "ix_device_token_recipient", columnList = "recipient_id"),
    ],
)
class DeviceTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID(0, 0)

    @Column(name = "recipient_id", nullable = false, length = 128)
    var recipientId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    var platform: DeviceToken.Platform = DeviceToken.Platform.ANDROID

    @Column(name = "token", nullable = false, length = 256)
    var token: String = ""

    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant = Instant.EPOCH

    @Column(name = "disabled_at")
    var disabledAt: Instant? = null
}
