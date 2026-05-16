package com.example.notification.application.dto

import com.example.notification.domain.channel.ChannelType
import java.time.Instant

/**
 * DLQ list / stats / bulk 의 공통 필터. 모든 필드 optional.
 *
 * 필드 의미:
 * - [channelType] — `PUSH` / `EMAIL` / `SMS` / `KAKAO_ALIMTALK`.
 * - [topic] — `notification.delivery.<channel>` 형식. controller 에서 [channelType] 으로 환원
 *   후 도메인에는 [channelType] 만 넘김. 둘 다 주면 [channelType] 우선.
 * - [consumerGroup] — 현재 코드 베이스는 channel 별 group-id 1개 (`notification-hub-<channel>`)
 *   만 사용. 다른 값을 주면 결과 0건 — 호환성 자리만 잡아둠.
 * - [from] / [to] — `delivery_attempt.created_at` 범위.
 * - [errorContains] — `failure_reason LIKE %v%` (대소문자 무시는 DB collation 에 의존).
 */
@JvmRecord
data class DlqEntryFilter(
    val channelType: ChannelType?,
    val topic: String?,
    val consumerGroup: String?,
    val from: Instant?,
    val to: Instant?,
    val errorContains: String?,
) {

    /** [topic] 이 채워졌고 [channelType] 이 비었으면 topic 에서 channelType 을 유도. */
    fun resolvedChannelType(): ChannelType? {
        if (channelType != null) return channelType
        if (topic.isNullOrBlank()) return null
        val prefix = "notification.delivery."
        if (!topic.startsWith(prefix)) return null
        val suffix = topic.substring(prefix.length).uppercase()
        return ChannelType.entries.firstOrNull { it.name == suffix }
    }

    /**
     * [consumerGroup] 이 명시되었으나 현재 시스템이 지원하지 않는 그룹인지. 결과 0건을 보장하기
     * 위한 short-circuit 용. 현재 알고 있는 group prefix 는 `notification-hub`.
     */
    fun isUnknownConsumerGroup(): Boolean =
        !consumerGroup.isNullOrBlank() && !consumerGroup.startsWith("notification-hub")

    companion object {
        @JvmField
        val EMPTY: DlqEntryFilter = DlqEntryFilter(null, null, null, null, null, null)
    }
}
