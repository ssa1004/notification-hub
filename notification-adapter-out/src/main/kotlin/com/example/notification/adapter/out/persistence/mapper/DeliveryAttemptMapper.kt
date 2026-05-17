package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity
import com.example.notification.domain.channel.Channel
import com.example.notification.domain.delivery.DeliveryAttempt

object DeliveryAttemptMapper {

    @JvmStatic
    fun toEntity(a: DeliveryAttempt): DeliveryAttemptEntity = DeliveryAttemptEntity().apply {
        id = a.id
        notificationId = a.notificationId
        channelType = a.channel.type
        channelAddress = a.channel.address
        renderedTitle = a.renderedTitle
        renderedBody = a.renderedBody
        status = a.status
        retryCount = a.retryCount
        nextAttemptAt = a.nextAttemptAt
        completedAt = a.completedAt
        vendorMessageId = a.vendorMessageId
        failureReason = a.failureReason
        createdAt = a.createdAt
    }

    @JvmStatic
    fun toDomain(e: DeliveryAttemptEntity): DeliveryAttempt =
        DeliveryAttempt(
            e.id,
            e.notificationId,
            Channel(e.channelType, e.channelAddress),
            e.renderedTitle,
            e.renderedBody,
            e.createdAt,
            e.status,
            e.retryCount,
            e.nextAttemptAt,
            e.completedAt,
            e.vendorMessageId,
            e.failureReason,
        )
}
