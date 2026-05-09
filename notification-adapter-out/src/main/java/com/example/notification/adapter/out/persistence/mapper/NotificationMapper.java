package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.NotificationEntity;
import com.example.notification.domain.notification.Notification;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.IdempotencyKey;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.id());
        e.setIdempotencyKey(n.idempotencyKey().value());
        e.setRecipientId(n.recipientId().value());
        e.setKind(n.kind());
        e.setTitle(n.title());
        e.setBody(n.body());
        e.setPayloadJson(JsonMapper.writeMap(n.payload()));
        e.setTemplateKey(n.templateKey());
        e.setStatus(n.status());
        e.setCreatedAt(n.createdAt());
        return e;
    }

    public static Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                new IdempotencyKey(e.getIdempotencyKey()),
                new RecipientId(e.getRecipientId()),
                e.getKind(),
                e.getTitle(),
                e.getBody(),
                JsonMapper.readStringMap(e.getPayloadJson()),
                e.getTemplateKey(),
                e.getCreatedAt(),
                e.getStatus());
    }
}
