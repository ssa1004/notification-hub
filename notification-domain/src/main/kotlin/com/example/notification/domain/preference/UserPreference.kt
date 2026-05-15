package com.example.notification.domain.preference

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.notification.NotificationKind
import com.example.notification.domain.recipient.RecipientId
import java.time.ZoneId
import java.util.EnumMap
import java.util.EnumSet

/**
 * 사용자별 알림 선호도. 한 알림이 들어왔을 때 어느 채널로 보낼지를 최종 결정.
 *
 * 구성 요소:
 * - `allowedByKind` — 알림 종류별 (마케팅 / 거래 / 공지) opt-out
 * - `preferredChannels` — 알림 종류별 우선 사용 채널 (없으면 기본 정책)
 * - `quietHours` — 방해금지 시간 (null 이면 비활성)
 * - `timezone` — 위 시간 해석 기준
 *
 * 거래성 (TRANSACTIONAL) / 보안성 (SECURITY) 알림은 사용자가 opt-out 못합니다 — 법적 의무
 * (예: OTP, 결제 알림) 와 사기 방지. [NotificationKind.mandatory] 가 true 면 무시.
 *
 * allowedByKind / preferredChannels / quietHours / timezone 은 null 을 허용 — 각각
 * 빈 맵 / 빈 맵 / 비활성 / Asia/Seoul 로 기본 적용.
 */
class UserPreference(
    recipientId: RecipientId,
    allowedByKind: Map<NotificationKind, Boolean>?,
    preferredChannels: Map<NotificationKind, Set<ChannelType>>?,
    quietHours: QuietHours?,
    timezone: ZoneId?,
) {

    @get:JvmName("recipientId")
    val recipientId: RecipientId = recipientId

    private val allowedByKind: Map<NotificationKind, Boolean> =
        EnumMap<NotificationKind, Boolean>(NotificationKind::class.java).apply {
            if (allowedByKind != null) putAll(allowedByKind)
        }

    private val preferredChannels: Map<NotificationKind, Set<ChannelType>> =
        EnumMap<NotificationKind, Set<ChannelType>>(NotificationKind::class.java).apply {
            preferredChannels?.forEach { (k, v) -> put(k, EnumSet.copyOf(v)) }
        }

    @get:JvmName("quietHours")
    val quietHours: QuietHours? = quietHours

    @get:JvmName("timezone")
    val timezone: ZoneId = timezone ?: ZoneId.of("Asia/Seoul")

    /**
     * 이 종류의 알림이 사용자에게 허용되는가? mandatory 종류는 무조건 true.
     */
    fun isAllowed(kind: NotificationKind): Boolean {
        if (kind.mandatory()) {
            return true
        }
        return allowedByKind.getOrDefault(kind, true)
    }

    /**
     * 이 종류의 알림이 들어왔을 때 어느 채널을 우선 시도할지. 비어 있으면 기본 정책.
     */
    fun preferredChannelsFor(kind: NotificationKind): Set<ChannelType> =
        preferredChannels.getOrDefault(kind, emptySet())

    /**
     * 새 선호도로 갱신된 사본. 도메인 객체 자체는 불변.
     */
    fun withChannelOptOut(kind: NotificationKind, allowed: Boolean): UserPreference {
        require(!kind.mandatory()) {
            "mandatory notification kind cannot be opted-out: $kind"
        }
        val copy = EnumMap(allowedByKind)
        copy[kind] = allowed
        return UserPreference(recipientId, copy, preferredChannels, quietHours, timezone)
    }

    fun withPreferredChannels(kind: NotificationKind, channels: Set<ChannelType>): UserPreference {
        val copy = EnumMap(preferredChannels)
        copy[kind] = EnumSet.copyOf(channels)
        return UserPreference(recipientId, allowedByKind, copy, quietHours, timezone)
    }

    fun withQuietHours(quietHours: QuietHours?): UserPreference =
        UserPreference(recipientId, allowedByKind, preferredChannels, quietHours, timezone)

    fun withTimezone(timezone: ZoneId): UserPreference =
        UserPreference(recipientId, allowedByKind, preferredChannels, quietHours, timezone)

    companion object {
        @JvmStatic
        fun defaults(recipientId: RecipientId): UserPreference =
            UserPreference(
                recipientId,
                emptyMap(),
                emptyMap(),
                QuietHours.DEFAULT,
                ZoneId.of("Asia/Seoul"),
            )
    }
}
