package com.example.notification.application.dto

import com.example.notification.domain.delivery.DeliveryAttempt
import java.time.Instant
import java.util.UUID

/** DLQ 운영 화면 1줄. 본문은 길이만 보여주고 vendor message id / failure reason 등 진단 메타. */
@JvmRecord
data class DlqEntryView(
    val attemptId: UUID,
    val notificationId: UUID,
    val channelType: String,
    val channelAddressMasked: String,
    val status: String,
    val retryCount: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
    val failureReason: String?,
    val renderedBodyLength: Int,
) {

    companion object {
        @JvmStatic
        fun from(a: DeliveryAttempt): DlqEntryView =
            DlqEntryView(
                a.id,
                a.notificationId,
                a.channel.type.name,
                a.channel.toString(),
                a.status.name,
                a.retryCount,
                a.createdAt,
                a.completedAt,
                a.failureReason,
                a.renderedBody.length,
            )
    }
}
