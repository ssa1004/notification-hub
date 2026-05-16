package com.example.notification.application.port.out

import com.example.notification.application.dto.DlqBulkJob
import java.util.Optional
import java.util.UUID

/**
 * bulk-replay / bulk-discard 비동기 job 의 상태 보존.
 *
 * 현재 구현은 in-memory (재시작 시 손실). DB / Redis 로 옮기는 것은 어댑터 단 교체로 충분 —
 * 도메인/use case 는 이 port 만 알면 됨.
 */
interface DlqBulkJobRepository {

    /** RUNNING 상태로 새 job 생성. */
    fun create(job: DlqBulkJob)

    /** 같은 jobId 에 대해 진행도 / state / 결과를 덮어쓰기. */
    fun update(job: DlqBulkJob)

    fun findById(jobId: UUID): Optional<DlqBulkJob>
}
