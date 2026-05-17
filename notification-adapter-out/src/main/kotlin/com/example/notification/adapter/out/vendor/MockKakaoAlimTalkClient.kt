package com.example.notification.adapter.out.vendor

import com.example.notification.application.port.out.DeliveryGateway
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import io.github.resilience4j.retry.annotation.Retry
import java.io.IOException
import java.io.UncheckedIOException
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 카카오 알림톡 Mock.
 *
 * 응답 형식은 카카오 비즈메시지 plus friend 의 message id 를 흉내내 `KKO-{uuid}` 형태.
 *
 * vendor 정책상 야간 (KST 21:00~08:00) 발송은 정보성 알림톡 차단 — 차단되면
 * [VendorPermanentException] 으로 즉시 실패. ChannelResolver 단에서 야간 KAKAO_ALIMTALK
 * 을 채널 후보에서 제외하므로 1차 차단되지만 (선호도 / DND), 운영자가 강제 발송하거나 정책
 * 우회로 도달하더라도 vendor 단에서 final 거절. 광고성 알림톡과 무관한 SECURITY 알림이라도
 * vendor 정책은 동일.
 *
 * 실패 케이스:
 * - [VendorPermanentException] — TEMPLATE_NOT_FOUND (등록 안 된 템플릿). retry 무의미.
 * - [VendorPermanentException] — NIGHT_TIME_BLOCKED (야간 정책). retry 무의미.
 * - [VendorTransientException] — 5xx. retry 대상.
 * - [UncheckedIOException] — connection reset. retry 대상.
 */
@Component
open class MockKakaoAlimTalkClient(
    private val clock: Clock,
) : DeliveryGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${vendor.kakao.failure-rate:0.0}")
    private var failureRate: Double = 0.0

    @Value("\${vendor.kakao.enforce-night-block:false}")
    private var enforceNightBlock: Boolean = false

    override fun channelType(): ChannelType = ChannelType.KAKAO_ALIMTALK

    @Retry(name = "kakao")
    override fun dispatch(attempt: DeliveryAttempt): String {
        if (enforceNightBlock && isNightInKst()) {
            log.warn(
                "[MockKakaoAlimTalkClient] vendor 정책 차단 (NIGHT_TIME_BLOCKED) attemptId={}",
                attempt.id,
            )
            throw VendorPermanentException("Kakao NIGHT_TIME_BLOCKED (KST 21:00~08:00)")
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            when (ThreadLocalRandom.current().nextInt(3)) {
                0 -> {
                    log.warn(
                        "[MockKakaoAlimTalkClient] 영구 오류 (TEMPLATE_NOT_FOUND) attemptId={}",
                        attempt.id,
                    )
                    throw VendorPermanentException("Kakao 5004: TEMPLATE_NOT_FOUND")
                }
                1 -> {
                    log.warn(
                        "[MockKakaoAlimTalkClient] 일시 오류 (5xx) attemptId={}",
                        attempt.id,
                    )
                    throw VendorTransientException("Kakao 5xx: Internal Server Error")
                }
                else -> {
                    log.warn(
                        "[MockKakaoAlimTalkClient] 네트워크 오류 attemptId={}",
                        attempt.id,
                    )
                    throw UncheckedIOException(IOException("Kakao connection reset"))
                }
            }
        }
        val msgId = MSG_ID_PREFIX + UUID.randomUUID()
        log.info("[MockKakaoAlimTalkClient] dispatched attemptId={} msgId={}", attempt.id, msgId)
        return msgId
    }

    private fun isNightInKst(): Boolean {
        val now = LocalTime.now(clock.withZone(KST))
        // wraps midnight: 21:00 ≤ now OR now < 08:00
        return !now.isBefore(NIGHT_START) || now.isBefore(NIGHT_END)
    }

    companion object {
        /** 카카오 알림톡 message id prefix. */
        private const val MSG_ID_PREFIX = "KKO-"
        /** vendor 정책 — KST 야간 차단 시작 시각 (포함). */
        private val NIGHT_START: LocalTime = LocalTime.of(21, 0)
        /** vendor 정책 — KST 야간 차단 종료 시각 (제외). */
        private val NIGHT_END: LocalTime = LocalTime.of(8, 0)
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
