package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.dto.SendNotificationResult
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.notification.NotificationStatus
import java.util.UUID

@JvmRecord
data class SendNotificationResponse(
    val notificationId: UUID,
    val status: NotificationStatus,
    val dispatchedChannels: List<ChannelType>,
    val suppressionReason: String?,
) {

    companion object {
        @JvmStatic
        fun from(result: SendNotificationResult): SendNotificationResponse =
            SendNotificationResponse(
                result.notificationId,
                result.status,
                result.dispatchedChannels,
                result.suppressionReason,
            )
    }
}
