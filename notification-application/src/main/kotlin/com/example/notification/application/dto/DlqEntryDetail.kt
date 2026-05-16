package com.example.notification.application.dto

import com.example.notification.domain.delivery.DeliveryAttempt
import java.time.Instant
import java.util.UUID

/**
 * DLQ 단건 상세. list 의 [DlqEntryView] 는 화면 1줄 요약, 이건 detail 화면용 — full payload
 * (rendered title / body) + retry context (next attempt at, vendor message id) 까지 포함.
 *
 * channelAddress 는 raw 가 아닌 마스킹된 [com.example.notification.domain.channel.Channel.toString]
 * 형식 ("PUSH:p***p") — admin 화면에서도 PII 노출 막음.
 */
@JvmRecord
data class DlqEntryDetail(
    val attemptId: UUID,
    val notificationId: UUID,
    val channelType: String,
    val channelAddressMasked: String,
    val status: String,
    val retryCount: Int,
    val maxRetry: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
    val nextAttemptAt: Instant?,
    val vendorMessageId: String?,
    val failureReason: String?,
    val errorClass: String?,
    val renderedTitle: String,
    val renderedBody: String,
    /** Kafka topic 추정값 — `notification.delivery.<channel>`. 운영자가 replay 후 어디로 갈지 명시. */
    val expectedTopic: String,
) {

    companion object {
        @JvmStatic
        fun from(a: DeliveryAttempt): DlqEntryDetail =
            DlqEntryDetail(
                a.id,
                a.notificationId,
                a.channel.type.name,
                a.channel.toString(),
                a.status.name,
                a.retryCount,
                DeliveryAttempt.MAX_RETRY,
                a.createdAt,
                a.completedAt,
                a.nextAttemptAt,
                a.vendorMessageId,
                a.failureReason,
                DlqErrorClass.classify(a.failureReason),
                a.renderedTitle,
                a.renderedBody,
                "notification.delivery." + a.channel.type.name.lowercase(),
            )
    }
}

/**
 * failureReason 문자열에서 첫 token 을 잘라 error class 로 사용. 예:
 * - `"VendorTransientException: vendor down"` → `"VendorTransientException"`
 * - `"vendor down"` → `"vendor down"` (token 1개)
 * - null / blank → null
 *
 * 별도 enum 으로 강제 매핑하지 않음 — 새 예외 타입이 늘어나도 자동으로 포함되도록.
 */
object DlqErrorClass {

    @JvmStatic
    fun classify(failureReason: String?): String? {
        if (failureReason.isNullOrBlank()) return null
        val firstColon = failureReason.indexOf(':')
        val firstSpace = failureReason.indexOf(' ')
        val cut = when {
            firstColon > 0 && (firstSpace < 0 || firstColon < firstSpace) -> firstColon
            firstSpace > 0 -> firstSpace
            else -> failureReason.length
        }
        return failureReason.substring(0, cut).trim().ifEmpty { null }
    }
}
