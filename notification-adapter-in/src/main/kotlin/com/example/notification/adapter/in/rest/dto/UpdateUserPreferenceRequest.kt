package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.port.`in`.UpdateUserPreferenceUseCase
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.notification.NotificationKind
import com.example.notification.domain.preference.QuietHours
import java.time.LocalTime
import java.time.ZoneId

/**
 * 사용자 알림 선호도 변경 요청. 모든 필드 optional — null 이면 해당 항목 변경 없음.
 *
 * `disableQuietHours = true` 면 quietStart/quietEnd 무관하게 DND 비활성.
 */
@JvmRecord
data class UpdateUserPreferenceRequest(
    val kind: NotificationKind?,
    val allowed: Boolean?,
    val preferredChannels: Set<ChannelType>?,
    val quietStart: String?, // "22:00"
    val quietEnd: String?,   // "08:00"
    val disableQuietHours: Boolean?,
    val timezone: String?,   // "Asia/Seoul"
) {

    fun toCommand(recipientId: String): UpdateUserPreferenceUseCase.UpdateCommand {
        val qh = if (quietStart != null && quietEnd != null) {
            QuietHours(LocalTime.parse(quietStart), LocalTime.parse(quietEnd))
        } else {
            null
        }
        return UpdateUserPreferenceUseCase.UpdateCommand(
            recipientId,
            kind,
            allowed,
            preferredChannels,
            qh,
            disableQuietHours == true,
            timezone?.let { ZoneId.of(it) },
        )
    }
}
