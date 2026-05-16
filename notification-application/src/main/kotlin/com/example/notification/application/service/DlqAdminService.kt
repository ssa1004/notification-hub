package com.example.notification.application.service

import com.example.notification.application.dto.DlqEntryDetail
import com.example.notification.application.dto.DlqEntryFilter
import com.example.notification.application.dto.DlqEntryView
import com.example.notification.application.dto.DlqListPage
import com.example.notification.application.dto.DlqStats
import com.example.notification.application.exception.AttemptNotFoundException
import com.example.notification.application.exception.IllegalDlqOperationException
import com.example.notification.application.exception.UnauthorizedAdminException
import com.example.notification.application.port.`in`.DlqAdminUseCase
import com.example.notification.application.port.out.AuditLogger
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.application.port.out.OutboxPublisher
import com.example.notification.application.security.AdminContext
import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryRequested
import com.example.notification.domain.delivery.DeliveryStatus
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DLQ 단건 운영 서비스. 모든 메서드가 admin role 가드 → audit log 기록.
 *
 * replay 는 outbox 에 [DeliveryRequested] 재발행 — Kafka 에 다시 들어가 worker 가 재처리.
 * discard 는 DB 상태만 변경 (이벤트 발행 안 함).
 *
 * 다건 (bulk-*) 은 [DlqBulkAdminService] 로 분리 — 책임 격리 + 비동기 worker 의 별도 트랜잭션
 * 처리 / dry-run 가드 / job 상태 보존 등 다른 정책 모음.
 */
@Service
class DlqAdminService(
    private val repository: DeliveryAttemptRepository,
    private val outboxPublisher: OutboxPublisher,
    private val auditLogger: AuditLogger,
) : DlqAdminUseCase {

    @Transactional(readOnly = true)
    override fun list(cursor: UUID?, limit: Int): List<DlqEntryView> {
        requireAdmin()
        val safeLimit = clampPageSize(limit)
        return repository.findByStatusAfter(DeliveryStatus.EXHAUSTED, cursor, safeLimit)
            .map { DlqEntryView.from(it) }
    }

    @Transactional
    override fun replay(attemptId: UUID): DlqEntryView {
        requireAdmin()
        val saved = doReplay(attemptId)
        log.info("DLQ replay attemptId={}", saved.id)
        return DlqEntryView.from(saved)
    }

    @Transactional
    override fun discard(attemptId: UUID, reason: String?): DlqEntryView {
        requireAdmin()
        val saved = doDiscard(attemptId, reason ?: "(no reason)")
        log.info("DLQ discard attemptId={} reason={}", saved.id, reason)
        return DlqEntryView.from(saved)
    }

    @Transactional(readOnly = true)
    override fun search(filter: DlqEntryFilter, cursor: UUID?, size: Int): DlqListPage {
        requireAdmin()
        val safeSize = clampPageSize(size)
        if (filter.isUnknownConsumerGroup()) {
            return DlqListPage(emptyList(), null, safeSize)
        }
        val items = repository.searchExhausted(
            filter.resolvedChannelType(),
            filter.from,
            filter.to,
            filter.errorContains,
            cursor,
            safeSize,
        ).map { DlqEntryView.from(it) }
        // 결과가 safeSize 와 같으면 다음 페이지 가능성 — 마지막 id 를 cursor 로. 더 적으면 끝.
        val nextCursor = if (items.size == safeSize) items.last().attemptId else null
        return DlqListPage(items, nextCursor, safeSize)
    }

    @Transactional(readOnly = true)
    override fun detail(attemptId: UUID): Optional<DlqEntryDetail> {
        requireAdmin()
        return repository.findById(attemptId).map { DlqEntryDetail.from(it) }
    }

    @Transactional(readOnly = true)
    override fun stats(from: Instant?, to: Instant?, bucket: Duration?): DlqStats {
        requireAdmin()
        val now = Instant.now()
        val effectiveTo = to ?: now
        val effectiveFrom = from ?: effectiveTo.minus(Duration.ofHours(24))
        require(!effectiveFrom.isAfter(effectiveTo)) { "from must be <= to" }
        val effectiveBucket = bucket ?: Duration.ofHours(1)
        require(!effectiveBucket.isZero && !effectiveBucket.isNegative) {
            "bucket must be positive"
        }
        // 어댑터가 row 단위로 (bucketStart, channel, errorClass, count) 를 돌려주면 use case
        // 가 multi-axis 합계 / 차원별 집계를 만든다 — 어댑터는 group by 책임만.
        val rows = repository.aggregateExhaustedStats(effectiveFrom, effectiveTo, effectiveBucket)

        val byBucket = rows.groupBy { it.bucketStart }
            .map { (k, v) -> DlqStats.BucketCount(k, v.sumOf { r -> r.count }) }
            .sortedBy { it.bucketStart }
        val byChannel = rows.groupBy { it.channelType.name }
            .map { (k, v) -> DlqStats.KeyedCount(k, v.sumOf { r -> r.count }) }
            .sortedByDescending { it.count }
        val byErrorClass = rows.groupBy { it.errorClass ?: "(unknown)" }
            .map { (k, v) -> DlqStats.KeyedCount(k, v.sumOf { r -> r.count }) }
            .sortedByDescending { it.count }
        val total = rows.sumOf { it.count }
        return DlqStats(
            effectiveFrom,
            effectiveTo,
            effectiveBucket,
            total,
            byBucket,
            byChannel,
            byErrorClass,
        )
    }

    // ============================================================
    // package-internal — bulk service 가 batch 안에서 호출.
    // ============================================================

    /** EXHAUSTED → PENDING(retry=0) + outbox 재발행 + audit. 호출자가 admin 가드 책임. */
    internal fun doReplay(attemptId: UUID): DeliveryAttempt {
        val attempt = loadOrThrow(attemptId)
        if (attempt.status != DeliveryStatus.EXHAUSTED) {
            throw IllegalDlqOperationException(
                "replay only allowed on EXHAUSTED, was ${attempt.status}",
            )
        }
        attempt.replayFromExhausted()
        val saved = repository.save(attempt)

        outboxPublisher.publish(
            DELIVERY_TOPIC_PREFIX + saved.channel.type.name.lowercase(),
            saved.id.toString(),
            DeliveryRequested.of(saved.notificationId, saved.id, saved.channel.type),
        )

        auditLogger.log(
            "admin",
            "DLQ_REPLAY",
            mapOf(
                "attemptId" to saved.id.toString(),
                "notificationId" to saved.notificationId.toString(),
                "channel" to saved.channel.type.name,
            ),
        )
        return saved
    }

    /** EXHAUSTED → PERMANENTLY_FAILED + audit. 호출자가 admin 가드 책임. */
    internal fun doDiscard(attemptId: UUID, reason: String): DeliveryAttempt {
        val attempt = loadOrThrow(attemptId)
        if (attempt.status != DeliveryStatus.EXHAUSTED) {
            throw IllegalDlqOperationException(
                "discard only allowed on EXHAUSTED, was ${attempt.status}",
            )
        }
        attempt.discardFromExhausted(reason)
        val saved = repository.save(attempt)

        auditLogger.log(
            "admin",
            "DLQ_DISCARD",
            mapOf(
                "attemptId" to saved.id.toString(),
                "notificationId" to saved.notificationId.toString(),
                "reason" to reason,
            ),
        )
        return saved
    }

    private fun loadOrThrow(id: UUID): DeliveryAttempt =
        repository.findById(id).orElseThrow { AttemptNotFoundException(id) }

    companion object {
        const val DELIVERY_TOPIC_PREFIX: String = "notification.delivery."

        /** Controller / search 가 강제하는 page size 상한 — 그 이상은 cursor 로 페이지. */
        const val MAX_PAGE_SIZE: Int = 200

        private val log = LoggerFactory.getLogger(DlqAdminService::class.java)

        private fun clampPageSize(requested: Int): Int =
            minOf(maxOf(requested, 1), MAX_PAGE_SIZE)

        private fun requireAdmin() {
            if (!AdminContext.isAdmin()) {
                throw UnauthorizedAdminException("admin role required")
            }
        }
    }
}
