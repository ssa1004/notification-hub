package com.example.notification.adapter.out.vendor

import com.example.notification.application.port.out.DeliveryGateway
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import io.github.resilience4j.retry.annotation.Retry
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Twilio SMS Mock.
 *
 * 응답 형식은 Twilio Message SID — `SM` prefix + 32자 hex (총 34자). 실제 SDK 로
 * 교체 시 audit / 콜백 매칭 코드 호환.
 *
 * SMS body 가 GSM-7 기준 160자 (UTF-8 90B 정도) 초과 시 vendor 가 segment 단위로 청구 —
 * segment 추정치를 로그에 남겨 비용 가시성 제공.
 *
 * 실패 케이스:
 * - [VendorInvalidRecipientException] — Invalid 'To' Number (잘못된 형식). retry
 *   무의미. SMS 는 token 비활성화 분기를 안 타지만 의미적으로 식별자 무효 신호.
 * - [VendorTransientException] — 5xx (vendor 측 일시 오류). retry 대상.
 * - [UncheckedIOException] — connection reset. retry 대상.
 */
@Component
open class MockTwilioClient : DeliveryGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${vendor.twilio.failure-rate:0.0}")
    private var failureRate: Double = 0.0

    override fun channelType(): ChannelType = ChannelType.SMS

    @Retry(name = "twilio")
    override fun dispatch(attempt: DeliveryAttempt): String {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            when (ThreadLocalRandom.current().nextInt(3)) {
                0 -> {
                    log.warn(
                        "[MockTwilioClient] 수신자 무효 (Invalid 'To' Number) attemptId={}",
                        attempt.id,
                    )
                    throw VendorInvalidRecipientException(
                        "Twilio 21211: Invalid 'To' phone number",
                    )
                }
                1 -> {
                    log.warn("[MockTwilioClient] 일시 오류 (5xx) attemptId={}", attempt.id)
                    throw VendorTransientException("Twilio 503: Service Unavailable")
                }
                else -> {
                    log.warn("[MockTwilioClient] 네트워크 오류 attemptId={}", attempt.id)
                    throw UncheckedIOException(IOException("Twilio connection reset"))
                }
            }
        }
        val bodyBytes = attempt.renderedBody.toByteArray(StandardCharsets.UTF_8).size
        if (bodyBytes > SEGMENT_BYTE_LIMIT) {
            val segments = (bodyBytes + SEGMENT_BYTE_LIMIT - 1) / SEGMENT_BYTE_LIMIT
            log.info(
                "[MockTwilioClient] LMS billing applied attemptId={} bytes={} segments={}",
                attempt.id,
                bodyBytes,
                segments,
            )
        }
        val msgId = SID_PREFIX + UUID.randomUUID().toString().replace("-", "")
        log.info("[MockTwilioClient] dispatched attemptId={} msgId={}", attempt.id, msgId)
        return msgId
    }

    companion object {
        /** Twilio Message SID prefix. */
        private const val SID_PREFIX = "SM"
        /** UTF-8 기준 segment 추정 임계 (한글 본문 90B 이상이면 LMS / multi-segment 청구). */
        private const val SEGMENT_BYTE_LIMIT = 90
    }
}
