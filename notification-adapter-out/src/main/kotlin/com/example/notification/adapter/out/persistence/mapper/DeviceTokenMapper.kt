package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.DeviceTokenEntity
import com.example.notification.domain.device.DeviceToken
import com.example.notification.domain.recipient.RecipientId

object DeviceTokenMapper {

    @JvmStatic
    fun toEntity(d: DeviceToken): DeviceTokenEntity = DeviceTokenEntity().apply {
        id = d.id
        recipientId = d.recipientId.value
        platform = d.platform
        token = d.token
        registeredAt = d.registeredAt
        disabledAt = d.disabledAt
    }

    @JvmStatic
    fun toDomain(e: DeviceTokenEntity): DeviceToken =
        DeviceToken(
            e.id,
            RecipientId(e.recipientId),
            e.platform,
            e.token,
            e.registeredAt,
            e.disabledAt,
        )
}
