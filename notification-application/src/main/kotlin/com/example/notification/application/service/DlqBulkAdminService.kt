package com.example.notification.application.service

import com.example.notification.application.dto.DlqBulkJob
import com.example.notification.application.dto.DlqBulkResult
import com.example.notification.application.dto.DlqEntryFilter
import com.example.notification.application.exception.IllegalDlqOperationException
import com.example.notification.application.exception.UnauthorizedAdminException
import com.example.notification.application.port.`in`.DlqBulkAdminUseCase
import com.example.notification.application.port.out.AuditLogger
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.application.port.out.DlqBulkJobRepository
import com.example.notification.application.security.AdminContext
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executor
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * DLQ 다건 (bulk) 운영 서비스. dry-run 기본 + confirm=true 시 비동기 worker 가 batch 단위로 처리.
 *
 * 각 항목은 별도 트랜잭션 ([TransactionTemplate.execute]) 으로 처리 — 한 건 실패가 다른 건을
 * 롤백하지 않음 → partial failure 추적 가능. 단건 처리 로직은 [DlqAdminService.doReplay] /
 * [DlqAdminService.doDiscard] 재사용 (audit / outbox / 상태 가드 동일).
 *
 * worker pool 은 `dlqBulkExecutor` (core 1 / max 2) — 동시 1건만 실행. vendor 부하 + outbox
 * 폭주 방지. 결과는 [DlqBulkJobRepository] 에 보존 — 운영자가 polling.
 */
@Service
class DlqBulkAdminService(
    private val repository: DeliveryAttemptRepository,
    private val auditLogger: AuditLogger,
    private val bulkJobRepository: DlqBulkJobRepository,
    private val singleService: DlqAdminService,
    @Qualifier("dlqBulkExecutor") private val bulkExecutor: Executor,
    private val txTemplate: TransactionTemplate,
) : DlqBulkAdminUseCase {

    override fun bulkReplay(
        filter: DlqEntryFilter,
        confirm: Boolean,
        reason: String?,
    ): DlqBulkResult = runBulk(DlqBulkJob.Operation.REPLAY, filter, confirm, reason)

    override fun bulkDiscard(
        filter: DlqEntryFilter,
        confirm: Boolean,
        reason: String,
    ): DlqBulkResult {
        require(reason.isNotBlank()) { "reason is required for bulk discard" }
        return runBulk(DlqBulkJob.Operation.DISCARD, filter, confirm, reason)
    }

    override fun getBulkJob(jobId: UUID): Optional<DlqBulkJob> {
        requireAdmin()
        return bulkJobRepository.findById(jobId)
    }

    // ============================================================
    // internal helpers
    // ============================================================

    private fun runBulk(
        operation: DlqBulkJob.Operation,
        filter: DlqEntryFilter,
        confirm: Boolean,
        reason: String?,
    ): DlqBulkResult {
        requireAdmin()
        if (filter.isUnknownConsumerGroup()) {
            return DlqBulkResult.dryRun(0, emptyList())
        }

        val resolvedChannel = filter.resolvedChannelType()
        val estimated = txTemplate.execute {
            repository.countExhausted(resolvedChannel, filter.from, filter.to, filter.errorContains)
        } ?: 0L

        val sampleIds = if (estimated == 0L) {
            emptyList()
        } else {
            txTemplate.execute {
                repository.searchExhausted(
                    resolvedChannel,
                    filter.from,
                    filter.to,
                    filter.errorContains,
                    null,
                    BULK_SAMPLE_SIZE,
                ).map { it.id }
            } ?: emptyList()
        }

        if (!confirm) {
            auditLogger.log(
                "admin",
                "DLQ_BULK_${operation.name}_DRYRUN",
                mapOf(
                    "estimatedCount" to estimated,
                    "channel" to (resolvedChannel?.name ?: "(any)"),
                    "from" to (filter.from?.toString() ?: ""),
                    "to" to (filter.to?.toString() ?: ""),
                    "errorContains" to (filter.errorContains ?: ""),
                    "reason" to (reason ?: ""),
                ),
            )
            return DlqBulkResult.dryRun(estimated, sampleIds)
        }

        val jobId = UUID.randomUUID()
        val now = Instant.now()
        val job = DlqBulkJob(
            jobId,
            operation,
            DlqBulkJob.State.RUNNING,
            estimated,
            0,
            0,
            0,
            now,
            null,
            null,
        )
        bulkJobRepository.create(job)

        auditLogger.log(
            "admin",
            "DLQ_BULK_${operation.name}_START",
            mapOf(
                "jobId" to jobId.toString(),
                "estimatedCount" to estimated,
                "channel" to (resolvedChannel?.name ?: "(any)"),
                "from" to (filter.from?.toString() ?: ""),
                "to" to (filter.to?.toString() ?: ""),
                "errorContains" to (filter.errorContains ?: ""),
                "reason" to (reason ?: ""),
            ),
        )

        // 비동기 실행 — 호출 thread 의 admin context 는 worker 로 전파 안 됨. 명시적으로 set.
        val mdcSnapshot: Map<String, String>? = MDC.getCopyOfContextMap()
        bulkExecutor.execute {
            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot)
            try {
                AdminContext.set(true)
                executeBulk(jobId, operation, filter, reason)
            } finally {
                AdminContext.clear()
                MDC.clear()
            }
        }

        return DlqBulkResult.executing(jobId, estimated, sampleIds)
    }

    /**
     * 비동기 worker 본체. cursor 페이지로 EXHAUSTED 를 batch 단위로 끌어와 각 항목을 별도
     * 트랜잭션으로 처리. 한 항목이 실패해도 다른 항목은 계속 진행 — partial failure 추적.
     */
    private fun executeBulk(
        jobId: UUID,
        operation: DlqBulkJob.Operation,
        filter: DlqEntryFilter,
        reason: String?,
    ) {
        val resolvedChannel = filter.resolvedChannelType()
        var processed = 0L
        var success = 0L
        var failure = 0L
        var firstError: String? = null
        var cursor: UUID? = null

        while (true) {
            val batch = txTemplate.execute {
                repository.searchExhausted(
                    resolvedChannel,
                    filter.from,
                    filter.to,
                    filter.errorContains,
                    cursor,
                    BULK_BATCH_SIZE,
                )
            } ?: emptyList()
            if (batch.isEmpty()) break

            for (a in batch) {
                processed++
                try {
                    txTemplate.execute {
                        when (operation) {
                            DlqBulkJob.Operation.REPLAY -> singleService.doReplay(a.id)
                            DlqBulkJob.Operation.DISCARD ->
                                singleService.doDiscard(a.id, reason ?: "(bulk discard)")
                        }
                    }
                    success++
                } catch (e: IllegalDlqOperationException) {
                    // bulk 진행 중 status 가 바뀐 경우 — 자연스러운 skip. failure 로 카운트하지 X.
                    log.debug("DLQ bulk {} skip attempt={} reason={}", operation, a.id, e.message)
                } catch (e: Exception) {
                    failure++
                    if (firstError == null) firstError = "${e.javaClass.simpleName}: ${e.message}"
                    log.warn("DLQ bulk {} failed attempt={} reason={}", operation, a.id, e.message)
                }
            }

            // 진행도 publish (사용자가 GET 으로 폴링 가능)
            val startedAt = bulkJobRepository.findById(jobId).map { it.startedAt }
                .orElse(Instant.now())
            val total = maxOf(processed, bulkJobRepository.findById(jobId)
                .map { it.totalCount }.orElse(processed))
            bulkJobRepository.update(
                DlqBulkJob(
                    jobId,
                    operation,
                    DlqBulkJob.State.RUNNING,
                    total,
                    processed,
                    success,
                    failure,
                    startedAt,
                    null,
                    firstError,
                ),
            )

            cursor = batch.last().id
            if (batch.size < BULK_BATCH_SIZE) break
        }

        val finalState = when {
            failure == 0L -> DlqBulkJob.State.SUCCEEDED
            success == 0L -> DlqBulkJob.State.FAILED
            else -> DlqBulkJob.State.PARTIAL_FAILURE
        }
        val final = DlqBulkJob(
            jobId,
            operation,
            finalState,
            processed,
            processed,
            success,
            failure,
            bulkJobRepository.findById(jobId).map { it.startedAt }.orElse(Instant.now()),
            Instant.now(),
            firstError,
        )
        bulkJobRepository.update(final)
        auditLogger.log(
            "admin",
            "DLQ_BULK_${operation.name}_FINISH",
            mapOf(
                "jobId" to jobId.toString(),
                "processed" to processed,
                "success" to success,
                "failure" to failure,
                "state" to finalState.name,
            ),
        )
        log.info(
            "DLQ bulk {} finished jobId={} processed={} success={} failure={} state={}",
            operation, jobId, processed, success, failure, finalState,
        )
    }

    companion object {
        /** dry-run / execute 모두 보여줄 sample id 상한. */
        const val BULK_SAMPLE_SIZE: Int = 10

        /** bulk worker 가 한 번에 끌어오는 batch size — 너무 크면 long tx, 작으면 polling 오버헤드. */
        const val BULK_BATCH_SIZE: Int = 100

        private val log = LoggerFactory.getLogger(DlqBulkAdminService::class.java)

        private fun requireAdmin() {
            if (!AdminContext.isAdmin()) {
                throw UnauthorizedAdminException("admin role required")
            }
        }
    }
}
