package com.example.notification.application.port.out

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** DeliveryAttempt persistence port. */
interface DeliveryAttemptRepository {

    fun save(attempt: DeliveryAttempt): DeliveryAttempt

    fun saveAll(attempts: List<DeliveryAttempt>): List<DeliveryAttempt>

    fun findById(id: UUID): Optional<DeliveryAttempt>

    fun findByNotificationId(notificationId: UUID): List<DeliveryAttempt>

    /**
     * DLQ 운영용 cursor 페이지네이션. 정해진 status (보통 EXHAUSTED) 의 attempt 중 cursor 보다
     * id 가 큰 것을 createdAt 오름차순으로 N개. cursor 가 null 이면 처음부터.
     */
    fun findByStatusAfter(status: DeliveryStatus, cursor: UUID?, limit: Int): List<DeliveryAttempt>

    /**
     * DLQ 필터 조회. EXHAUSTED 항목 중 옵션 필터를 적용 후 id 오름차순으로 [limit] 개.
     *
     * - [channelType] null → 모든 채널
     * - [from] / [to] null → createdAt 무제한 (한쪽만 지정 가능)
     * - [errorContains] null → failureReason 무관, 지정 시 LIKE %v%
     * - [cursor] null → 처음부터
     *
     * 호출자는 [DeliveryAttempt.id] 로 다음 cursor 를 만들면 됨 (조회 결과의 마지막 id).
     */
    fun searchExhausted(
        channelType: ChannelType?,
        from: Instant?,
        to: Instant?,
        errorContains: String?,
        cursor: UUID?,
        limit: Int,
    ): List<DeliveryAttempt>

    /** [searchExhausted] 와 동일 조건의 총 개수. bulk dry-run 의 대상 estimate 에 사용. */
    fun countExhausted(
        channelType: ChannelType?,
        from: Instant?,
        to: Instant?,
        errorContains: String?,
    ): Long

    /**
     * DLQ stats — EXHAUSTED 항목을 createdAt 시간 bucket / channelType 별로 count.
     *
     * @return key = (시간 bucket 시작, channelType, errorClass), value = 개수
     */
    fun aggregateExhaustedStats(
        from: Instant?,
        to: Instant?,
        bucketDuration: java.time.Duration,
    ): List<DlqStatRow>
}

/**
 * stats 집계 한 행. errorClass 는 failureReason 의 첫 token (예: "VendorTransientException")
 * 또는 null. 어댑터 (DB) 단에서 group by 로 묶어 반환.
 */
@JvmRecord
data class DlqStatRow(
    val bucketStart: Instant,
    val channelType: ChannelType,
    val errorClass: String?,
    val count: Long,
)
