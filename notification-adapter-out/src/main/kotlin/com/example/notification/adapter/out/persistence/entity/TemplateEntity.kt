package com.example.notification.adapter.out.persistence.entity

import com.example.notification.domain.channel.ChannelType
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
    name = "template",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_template_key_locale_channel",
            columnNames = ["template_key", "locale", "channel_type"],
        ),
    ],
    indexes = [
        Index(name = "ix_template_key", columnList = "template_key"),
    ],
)
class TemplateEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID(0, 0)

    @Column(name = "template_key", nullable = false, length = 128)
    var templateKey: String = ""

    @Column(name = "locale", nullable = false, length = 16)
    var locale: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    var channelType: ChannelType = ChannelType.PUSH

    @Column(name = "title_template", nullable = false, length = 200)
    var titleTemplate: String = ""

    @Column(name = "body_template", nullable = false, length = 4000)
    var bodyTemplate: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH
}
