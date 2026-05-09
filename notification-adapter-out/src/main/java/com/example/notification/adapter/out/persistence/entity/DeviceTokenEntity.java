package com.example.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "device_token",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_device_token_value", columnNames = "token"),
        indexes = @Index(name = "ix_device_token_recipient", columnList = "recipient_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, length = 128)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private com.example.notification.domain.device.DeviceToken.Platform platform;

    @Column(name = "token", nullable = false, length = 256)
    private String token;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;
}
