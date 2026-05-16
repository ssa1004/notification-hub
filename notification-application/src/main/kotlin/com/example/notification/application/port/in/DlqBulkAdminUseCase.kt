package com.example.notification.application.port.`in`

import com.example.notification.application.dto.DlqBulkJob
import com.example.notification.application.dto.DlqBulkResult
import com.example.notification.application.dto.DlqEntryFilter
import java.util.Optional
import java.util.UUID

/**
 * DLQ 다건 작업 — bulk-replay / bulk-discard + 비동기 job 진행도 조회.
 *
 * dry-run 강제 + idempotency / partial failure / audit 정책은 ADR-0015 참조. 분리 이유:
 * - 단건 endpoint (호환 보존) 와 다건 endpoint (위험 작업 + 비동기) 의 책임 분리.
 * - 단건만 필요한 호출자는 [DlqAdminUseCase] 만 의존하면 됨.
 */
interface DlqBulkAdminUseCase {

    /**
     * bulk-replay. [confirm] = false (default) 면 dry-run — 대상 개수 + sample id 만 반환.
     * true 면 비동기 job 시작 후 jobId 반환. job 결과는 [getBulkJob] 으로 조회.
     */
    fun bulkReplay(filter: DlqEntryFilter, confirm: Boolean, reason: String?): DlqBulkResult

    /** bulk-discard. dry-run 의미는 [bulkReplay] 와 동일. [reason] 필수. */
    fun bulkDiscard(filter: DlqEntryFilter, confirm: Boolean, reason: String): DlqBulkResult

    /** 비동기 bulk job 상태 조회. */
    fun getBulkJob(jobId: UUID): Optional<DlqBulkJob>
}
