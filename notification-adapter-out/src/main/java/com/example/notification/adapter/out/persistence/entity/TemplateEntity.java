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
        name = "template",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_template_key_locale_channel",
                        columnNames = {"template_key", "locale", "channel_type"}),
        indexes = @Index(name = "ix_template_key", columnList = "template_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_key", nullable = false, length = 128)
    private String templateKey;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    private com.example.notification.domain.channel.ChannelType channelType;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(name = "body_template", nullable = false, length = 4000)
    private String bodyTemplate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
