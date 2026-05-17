package com.example.notification.adapter.`in`.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * vendor 별 webhook HMAC 서명 secret. application.yml 에서:
 *
 * ```
 * webhook:
 *   secrets:
 *     fcm: ${WEBHOOK_SECRET_FCM:}
 *     ses: ${WEBHOOK_SECRET_SES:}
 *     twilio: ${WEBHOOK_SECRET_TWILIO:}
 *     kakao: ${WEBHOOK_SECRET_KAKAO:}
 * ```
 *
 * vendor 식별은 요청 헤더 `X-Notification-Hub-Vendor` 로. 등록 안 된 vendor 가
 * 들어오거나 secret 미설정이면 fail-closed (요청 거절).
 */
@Component
@ConfigurationProperties(prefix = "webhook")
class WebhookSecrets {

    /**
     * Spring Boot relaxed binding 이 setter / mutable getter 둘 다 지원하지만, 외부에서
     * `secrets[fcm]=xxx` 로 채워질 수 있도록 mutable map 노출.
     */
    val secrets: MutableMap<String, String> = HashMap()

    /** vendor 의 secret 반환. 없거나 빈 문자열이면 null. */
    fun secretFor(vendor: String?): String? {
        if (vendor == null) return null
        val s = secrets[vendor.lowercase()]
        return if (s.isNullOrBlank()) null else s
    }
}
