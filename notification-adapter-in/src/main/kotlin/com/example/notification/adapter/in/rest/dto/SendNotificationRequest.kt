package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.dto.SendNotificationCommand
import com.example.notification.domain.notification.NotificationKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 알림 전송 요청 body. recipientId / kind 는 필수. title/body 와 templateKey 는 둘 중 하나가
 * 채워져야 하며 (애플리케이션 단 검증) — 여기서는 단순 길이 제한만 부여.
 *
 * `@JvmRecord` + `@field:` validation 으로 Java caller 의 record 시그니처와 호환 (springdoc /
 * jackson / bean validation 모두 record 컴포넌트 메타데이터를 통해 동작).
 */
@JvmRecord
data class SendNotificationRequest(
    @field:NotBlank @field:Size(max = 128) val recipientId: String?,
    @field:NotNull val kind: NotificationKind?,
    @field:Size(max = 200) val title: String?,
    @field:Size(max = 4000) val body: String?,
    val payload: Map<String, String>?,
    @field:Size(max = 128) val templateKey: String?,
) {

    fun toCommand(idempotencyKey: String): SendNotificationCommand =
        SendNotificationCommand(
            idempotencyKey, recipientId, kind, title, body, payload, templateKey,
        )
}
