package com.example.notification.application.port.`in`

import com.example.notification.application.dto.DlqEntryView
import java.util.UUID

/**
 * DLQ (EXHAUSTED 상태 DeliveryAttempt) 운영. 운영자만 호출.
 *
 * 3개 동작:
 * - [list] — 페이지네이션 조회 (id cursor)
 * - [replay] — EXHAUSTED 를 PENDING (retry=0) 으로 환원 + Outbox 재발행
 * - [discard] — EXHAUSTED 를 PERMANENTLY_FAILED 로 영구 종료 (audit 보존)
 */
interface DlqAdminUseCase {

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
}
