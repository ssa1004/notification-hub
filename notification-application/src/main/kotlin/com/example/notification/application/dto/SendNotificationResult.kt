package com.example.notification.application.dto

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.notification.NotificationStatus
import java.util.UUID

/**
 * SendNotificationUseCase 응답. 발송 시도가 어느 채널로 fan-out 되었는지 알려줍니다.
 *
 * status SUPPRESSED 면 dispatchedChannels 는 빈 리스트.
 */
@JvmRecord
data class SendNotificationResult(
    val notificationId: UUID,
    val status: NotificationStatus,
    val dispatchedChannels: List<ChannelType>,
    val suppressionReason: String?,
)
