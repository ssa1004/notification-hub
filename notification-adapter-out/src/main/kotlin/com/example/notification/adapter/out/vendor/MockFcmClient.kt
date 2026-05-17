package com.example.notification.adapter.out.vendor

import com.example.notification.application.port.out.DeliveryGateway
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import io.github.resilience4j.retry.annotation.Retry
import java.io.IOException
import java.io.UncheckedIOException
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * FCM 호출의 Mock. 실제 SDK 의존성을 추가하지 않고 학습용으로 동작 시뮬레이션.
 *
 * 응답 형식은 FCM HTTP v1 규격을 흉내내 `projects/{project}/messages/{id}` 형태로
 * 반환 — 실제 SDK 로 교체 시 호출 측 (audit / 로깅 / 콜백 매칭) 코드가 그대로 동작하도록.
 *
 * `vendor.fcm.failure-rate` (0.0~1.0) 비율로 무작위 실패. 실패는 4가지 케이스 중 하나:
 *
 * - [VendorInvalidRecipientException] — NOT_REGISTERED (단말 토큰 unregister).
 *   retry 무의미 + token 비활성화 대상.
 * - [VendorPermanentException] — INVALID_ARGUMENT (페이로드 형식 오류). retry 무의미
 *   하지만 token 자체는 멀쩡 — 비활성화 X.
 * - [VendorTransientException] — UNAVAILABLE (5xx). Resilience4j retry 대상.
 * - [UncheckedIOException] — connection reset. Resilience4j retry 대상.
 *
 * Resilience4j @Retry 가 CGLIB proxy 를 만들 수 있도록 open class.
 */
@Component
open class MockFcmClient : DeliveryGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${vendor.fcm.failure-rate:0.0}")
    private var failureRate: Double = 0.0

    override fun channelType(): ChannelType = ChannelType.PUSH

    @Retry(name = "fcm")
    override fun dispatch(attempt: DeliveryAttempt): String {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            when (ThreadLocalRandom.current().nextInt(4)) {
                0 -> {
                    log.warn("[MockFcmClient] 수신자 무효 (NOT_REGISTERED) attemptId={}", attempt.id)
                    throw VendorInvalidRecipientException(
                        "FCM messaging/registration-token-not-registered",
                    )
                }
                1 -> {
                    log.warn("[MockFcmClient] 영구 오류 (INVALID_ARGUMENT) attemptId={}", attempt.id)
                    throw VendorPermanentException("FCM messaging/invalid-argument")
                }
                2 -> {
                    log.warn("[MockFcmClient] 일시 오류 (UNAVAILABLE) attemptId={}", attempt.id)
                    throw VendorTransientException("FCM messaging/server-unavailable")
                }
                else -> {
                    log.warn("[MockFcmClient] 네트워크 오류 attemptId={}", attempt.id)
                    throw UncheckedIOException(IOException("FCM connection reset"))
                }
            }
        }
        val msgId = MSG_ID_FORMAT.format(UUID.randomUUID())
        log.info(
            "[MockFcmClient] dispatched attemptId={} address={} msgId={}",
            attempt.id,
            attempt.channel,
            msgId,
        )
        return msgId
    }

    companion object {
        /** FCM HTTP v1 의 message name 포맷. `projects/{project}/messages/{id}`. */
        private const val MSG_ID_FORMAT = "projects/notification-hub/messages/%s"
    }
}
