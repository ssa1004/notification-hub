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
 * AWS SES Mock.
 *
 * 응답 형식은 SES SendEmail 의 MessageId — RFC 5322 Message-ID 와 비슷한 형태로
 * `<{uuid}@email.amazonses.com>` 반환. 실제 SDK 로 교체 시 audit / 콜백 매칭 코드가
 * 그대로 동작.
 *
 * 실패 케이스:
 * - [VendorPermanentException] — MessageRejected (sandbox / 미인증 도메인). retry 무의미.
 * - [VendorTransientException] — Throttling (sending quota 초과). retry 대상.
 * - [UncheckedIOException] — connection reset. retry 대상.
 */
@Component
open class MockSesClient : DeliveryGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${vendor.ses.failure-rate:0.0}")
    private var failureRate: Double = 0.0

    override fun channelType(): ChannelType = ChannelType.EMAIL

    @Retry(name = "ses")
    override fun dispatch(attempt: DeliveryAttempt): String {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            when (ThreadLocalRandom.current().nextInt(3)) {
                0 -> {
                    log.warn("[MockSesClient] 영구 오류 (MessageRejected) attemptId={}", attempt.id)
                    throw VendorPermanentException(
                        "SES MessageRejected: Email address not verified",
                    )
                }
                1 -> {
                    log.warn("[MockSesClient] 일시 오류 (Throttling) attemptId={}", attempt.id)
                    throw VendorTransientException(
                        "SES Throttling: Maximum sending rate exceeded",
                    )
                }
                else -> {
                    log.warn("[MockSesClient] 네트워크 오류 attemptId={}", attempt.id)
                    throw UncheckedIOException(IOException("SES connection reset"))
                }
            }
        }
        val msgId = MSG_ID_FORMAT.format(UUID.randomUUID())
        log.info("[MockSesClient] dispatched attemptId={} msgId={}", attempt.id, msgId)
        return msgId
    }

    companion object {
        /** SES MessageId 포맷 — `<{uuid}@email.amazonses.com>`. */
        private const val MSG_ID_FORMAT = "<%s@email.amazonses.com>"
    }
}
