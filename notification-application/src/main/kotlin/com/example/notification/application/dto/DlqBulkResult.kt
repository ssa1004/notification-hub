package com.example.notification.application.dto

import java.time.Instant
import java.util.UUID

/**
 * bulk-replay / bulk-discard 의 응답.
 *
 * 두 모드:
 * - **dry-run** (default 또는 `confirm=false`) — DB 에는 손 안 댐. [estimatedCount] 와
 *   [sampleAttemptIds] (최대 10개) 만 채워 미리보기 제공. [jobId] = null.
 * - **execute** (`confirm=true`) — 비동기 job 시작. [jobId] 로 [DlqBulkJob] 진행 추적.
 *   [estimatedCount] = 시작 시점의 대상 개수 추정 (실행 중 다른 EXHAUSTED 가 새로 늘어도
 *   이번 job 은 처음에 lock 한 id set 만 처리).
 *
 * [sampleAttemptIds] 는 사람 확인용 — 10개 초과면 잘림.
 */
@JvmRecord
data class DlqBulkResult(
    val mode: Mode,
    val estimatedCount: Long,
    val sampleAttemptIds: List<UUID>,
    val jobId: UUID?,
    val startedAt: Instant?,
) {

    enum class Mode {
        DRY_RUN,
        EXECUTING,
    }

    companion object {
        @JvmStatic
        fun dryRun(estimated: Long, samples: List<UUID>): DlqBulkResult =
            DlqBulkResult(Mode.DRY_RUN, estimated, samples, null, null)

        @JvmStatic
        fun executing(jobId: UUID, estimated: Long, samples: List<UUID>): DlqBulkResult =
            DlqBulkResult(Mode.EXECUTING, estimated, samples, jobId, Instant.now())
    }
}
