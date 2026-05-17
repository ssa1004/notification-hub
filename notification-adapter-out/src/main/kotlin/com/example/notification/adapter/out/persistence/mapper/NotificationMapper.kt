package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.NotificationEntity
import com.example.notification.domain.notification.Notification
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.IdempotencyKey

object NotificationMapper {

    @JvmStatic
    fun toEntity(n: Notification): NotificationEntity = NotificationEntity().apply {
        id = n.id
        idempotencyKey = n.idempotencyKey.value
        recipientId = n.recipientId.value
        kind = n.kind
        title = n.title
        body = n.body
        payloadJson = JsonMapper.writeMap(n.payload())
        templateKey = n.templateKey
        status = n.status
        createdAt = n.createdAt
    }

    @JvmStatic
    fun toDomain(e: NotificationEntity): Notification =
        Notification(
            e.id,
            IdempotencyKey(e.idempotencyKey),
            RecipientId(e.recipientId),
            e.kind,
            e.title,
            e.body,
            JsonMapper.readStringMap(e.payloadJson),
            e.templateKey,
            e.createdAt,
            e.status,
        )
}
