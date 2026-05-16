package com.example.notification.application.port.`in`

import com.example.notification.application.dto.DlqEntryDetail
import com.example.notification.application.dto.DlqEntryFilter
import com.example.notification.application.dto.DlqEntryView
import com.example.notification.application.dto.DlqListPage
import com.example.notification.application.dto.DlqStats
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * DLQ (EXHAUSTED 상태 DeliveryAttempt) 운영. 운영자만 호출.
 *
 * 기존 ([list] / [replay] / [discard]) 는 호환 위해 시그니처 그대로 유지. ADR-0015 에서
 * 필터 / 상세 / 통계 메서드 ([search] / [detail] / [stats]) 추가. bulk 작업은 별도
 * [DlqBulkAdminUseCase] 로 분리.
 */
interface DlqAdminUseCase {

    // --- 기존 (ADR-0012). 호환성 위해 시그니처 변경 X. ---

    fun list(cursor: UUID?, limit: Int): List<DlqEntryView>

    /**
     * EXHAUSTED → PENDING (retry=0) 환원 후 channel 별 Kafka topic 으로 DeliveryRequested
     * 재발행. 호출자가 admin 이 아니면
     * [com.example.notification.application.exception.UnauthorizedAdminException].
     */
    fun replay(attemptId: UUID): DlqEntryView

    /**
     * EXHAUSTED → PERMANENTLY_FAILED. 재발송 안 함. failureReason 에 "discarded: <reason>"
     * append. audit trail 만 유지.
     */
    fun discard(attemptId: UUID, reason: String?): DlqEntryView

    // --- 확장 (ADR-0015). ---

    /**
     * filter 조건으로 cursor 페이지네이션. [size] 1~200 사이로 캡.
     *
     * 결과의 [DlqListPage.nextCursor] 가 null 이면 마지막 페이지.
     */
    fun search(filter: DlqEntryFilter, cursor: UUID?, size: Int): DlqListPage

    /**
     * 단건 상세 — full rendered title / body + retry context. 없으면 [Optional.empty]. 호출자는
     * controller 단에서 404 로 매핑.
     */
    fun detail(attemptId: UUID): Optional<DlqEntryDetail>

    /**
     * 시간 [from]~[to] 범위의 EXHAUSTED 항목을 [bucket] 단위로 집계. [from] / [to] null 이면 각각
     * "최근 24h 시작" / "now" 로 대체. bucket null 이면 1시간.
     */
    fun stats(from: Instant?, to: Instant?, bucket: Duration?): DlqStats
}
