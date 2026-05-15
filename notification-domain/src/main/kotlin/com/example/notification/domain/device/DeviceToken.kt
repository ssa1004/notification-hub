package com.example.notification.domain.device

import com.example.notification.domain.recipient.RecipientId
import java.time.Instant
import java.util.UUID

/**
 * 모바일 push 채널의 device token (FCM / APNs). 한 사용자가 여러 디바이스를 가질 수 있습니다.
 *
 * 토큰은 vendor 가 발급한 raw string. 같은 디바이스라도 OS 가 토큰을 회전시키면 새 row
 * 로 등록되고 과거 토큰은 [disabledAt] 이 설정됩니다.
 *
 * vendor 콜백에서 InvalidRegistration / NotRegistered 응답을 받으면 즉시
 * [disable] 처리하여 다음 발송에서 건너뜀.
 *
 * 전체 인자 생성자는 persistence reconstitution 용 (mapper 가 호출). 신규 생성은
 * companion 의 [register] factory 사용.
 */
class DeviceToken(
    id: UUID,
    recipientId: RecipientId,
    platform: Platform,
    token: String,
    registeredAt: Instant,
    disabledAt: Instant?,
) {

    @get:JvmName("id")
    val id: UUID = id

    @get:JvmName("recipientId")
    val recipientId: RecipientId = recipientId

    @get:JvmName("platform")
    val platform: Platform = platform

    @get:JvmName("token")
    val token: String = token

    @get:JvmName("registeredAt")
    val registeredAt: Instant = registeredAt

    @get:JvmName("disabledAt")
    var disabledAt: Instant? = disabledAt
        private set

    init {
        require(token.length in 32..256) { "invalid device token length" }
    }

    fun disable() {
        if (disabledAt == null) {
            disabledAt = Instant.now()
        }
    }

    fun isActive(): Boolean = disabledAt == null

    enum class Platform {
        ANDROID,
        IOS,
        WEB,
    }

    companion object {
        @JvmStatic
        fun register(recipientId: RecipientId, platform: Platform, token: String): DeviceToken =
            DeviceToken(UUID.randomUUID(), recipientId, platform, token, Instant.now(), null)
    }
}
