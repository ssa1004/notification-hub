package com.example.notification.domain.delivery

import com.example.notification.domain.channel.Channel
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/**
 * 한 알림이 한 채널로 발송 시도되는 단위. notification 1 : N attempt.
 *
 * retry 횟수 / 다음 시도 시각 / 최종 실패 사유 등 vendor 호출 라이프사이클을 담습니다.
 *
 * [renderedTitle] / [renderedBody] 는 템플릿 + payload 변수 치환이 끝난 최종 텍스트입니다.
 * attempt 단위로 보관하는 이유: 같은 알림이라도 채널별로 본문이 달라지고 (SMS 90B 제한,
 * 이메일 풀 본문) audit / 재현에 필요.
 *
 * 전체 인자 생성자는 persistence reconstitution 용 (mapper 가 호출). 신규 생성은
 * companion 의 [create] factory 사용.
 */
class DeliveryAttempt(
    id: UUID,
    notificationId: UUID,
    channel: Channel,
    renderedTitle: String,
    renderedBody: String,
    createdAt: Instant,
    status: DeliveryStatus,
    retryCount: Int,
    nextAttemptAt: Instant?,
    completedAt: Instant?,
    vendorMessageId: String?,
    failureReason: String?,
) {

    @get:JvmName("id")
    val id: UUID = id

    @get:JvmName("notificationId")
    val notificationId: UUID = notificationId

    @get:JvmName("channel")
    val channel: Channel = channel

    @get:JvmName("renderedTitle")
    val renderedTitle: String = renderedTitle

    @get:JvmName("renderedBody")
    val renderedBody: String = renderedBody

    @get:JvmName("createdAt")
    val createdAt: Instant = createdAt

    @get:JvmName("status")
    var status: DeliveryStatus = status
        private set

    @get:JvmName("retryCount")
    var retryCount: Int = retryCount
        private set

    @get:JvmName("nextAttemptAt")
    var nextAttemptAt: Instant? = nextAttemptAt
        private set

    @get:JvmName("completedAt")
    var completedAt: Instant? = completedAt
        private set

    @get:JvmName("vendorMessageId")
    var vendorMessageId: String? = vendorMessageId
        private set

    @get:JvmName("failureReason")
    var failureReason: String? = failureReason
        private set

    /** Worker 가 vendor 호출 직전 호출. PENDING → DISPATCHING. */
    fun markDispatching() {
        check(status == DeliveryStatus.PENDING) {
            "dispatch only allowed from PENDING, was $status"
        }
        status = DeliveryStatus.DISPATCHING
    }

    /** vendor 호출 성공. */
    fun markSucceeded(vendorMessageId: String?) {
        check(status == DeliveryStatus.DISPATCHING) {
            "succeed only allowed from DISPATCHING, was $status"
        }
        status = DeliveryStatus.SUCCEEDED
        completedAt = Instant.now()
        this.vendorMessageId = vendorMessageId
    }

    /**
     * vendor 호출 실패. retry 한도 안이면 PENDING + 다음 시도 시각 계산. 초과면 EXHAUSTED.
     */
    fun markFailed(reason: String?) {
        check(status == DeliveryStatus.DISPATCHING) {
            "fail only allowed from DISPATCHING, was $status"
        }
        failureReason = reason
        if (retryCount + 1 >= MAX_RETRY) {
            status = DeliveryStatus.EXHAUSTED
            completedAt = Instant.now()
        } else {
            retryCount++
            status = DeliveryStatus.PENDING
            nextAttemptAt = Instant.now().plus(backoffFor(retryCount))
        }
    }

    fun isFinal(): Boolean =
        status == DeliveryStatus.SUCCEEDED ||
            status == DeliveryStatus.EXHAUSTED ||
            status == DeliveryStatus.PERMANENTLY_FAILED

    /**
     * 운영자가 EXHAUSTED 항목을 다시 발송 큐로 환원. retryCount 0 으로 초기화 → 새 attempt
     * 처럼 MAX_RETRY 만큼 다시 시도 가능. completedAt / vendorMessageId / failureReason 은
     * 다음 호출이 갱신.
     */
    fun replayFromExhausted() {
        check(status == DeliveryStatus.EXHAUSTED) {
            "replay only allowed from EXHAUSTED, was $status"
        }
        status = DeliveryStatus.PENDING
        retryCount = 0
        nextAttemptAt = Instant.now()
        completedAt = null
    }

    /**
     * 운영자가 EXHAUSTED 항목을 영구 종료. audit trail 만 남기고 재발송 불가능. dispatch
     * worker 가 상태 체크 후 무시.
     */
    fun discardFromExhausted(reason: String) {
        check(status == DeliveryStatus.EXHAUSTED) {
            "discard only allowed from EXHAUSTED, was $status"
        }
        status = DeliveryStatus.PERMANENTLY_FAILED
        completedAt = Instant.now()
        failureReason = (if (failureReason == null) "" else "$failureReason | ") + "discarded: $reason"
    }

    companion object {
        /** Resilience4j retry 상한과 정합. config 변경 시 같이 바뀜. */
        const val MAX_RETRY: Int = 5

        /** 새 PENDING attempt 생성. */
        @JvmStatic
        fun create(
            notificationId: UUID,
            channel: Channel,
            renderedTitle: String,
            renderedBody: String,
        ): DeliveryAttempt {
            val now = Instant.now()
            return DeliveryAttempt(
                UUID.randomUUID(),
                notificationId,
                channel,
                renderedTitle,
                renderedBody,
                now,
                DeliveryStatus.PENDING,
                0,
                now, // 즉시 발송 가능
                null,
                null,
                null,
            )
        }

        /**
         * Exponential backoff with jitter. base 1s, factor 2, jitter ±25%, cap 60s.
         *
         * retry=1 → 1s, retry=2 → 2s, retry=3 → 4s, retry=4 → 8s (마지막 재시도). cap 60s 는 retry≥7 에서 적용.
         */
        @JvmStatic
        fun backoffFor(retry: Int): Duration {
            val baseMs = (1000L * 2.0.pow(retry - 1)).toLong()
            val capped = min(baseMs, 60_000L)
            // 결정성: jitter 는 worker 의 sleep 단계에서 적용. 도메인은 base 만 보장.
            return Duration.ofMillis(capped)
        }
    }
}
