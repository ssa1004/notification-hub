package com.example.notification.domain.recipient

import com.example.notification.domain.channel.Channel
import com.example.notification.domain.shared.Locale
import java.time.ZoneId

/**
 * 알림 수신 대상. RecipientId + 등록된 모든 raw 채널 + locale + timezone 을 묶어 표현합니다.
 *
 * 여기서의 channels 는 사용자가 가진 주소록입니다. 실제로 어느 채널이 활성화되었는지는
 * `UserPreference` 가 결정합니다 (opt-out 처리).
 *
 * locale / timezone 은 null 을 허용 — 각각 ko-kr / Asia/Seoul 로 기본 적용.
 */
class Recipient(
    id: RecipientId,
    channels: List<Channel>,
    locale: Locale?,
    timezone: ZoneId?,
) {

    @get:JvmName("id")
    val id: RecipientId = id

    @get:JvmName("channels")
    val channels: List<Channel> = java.util.List.copyOf(channels)

    @get:JvmName("locale")
    val locale: Locale = locale ?: Locale.KO_KR

    @get:JvmName("timezone")
    val timezone: ZoneId = timezone ?: ZoneId.of("Asia/Seoul")
}
