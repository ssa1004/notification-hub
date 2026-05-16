package com.example.notification.application.service

import com.example.notification.application.dto.DlqEntryView
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
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DLQ 운영 서비스. 모든 메서드가 admin role 가드 → audit log 기록.
 *
 * replay 시 outbox 에 [DeliveryRequested] 재발행 — Kafka 에 다시 들어가 worker 가
 * 재처리. discard 는 DB 상태만 변경 (이벤트 발행 안 함).
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
        val safeLimit = minOf(maxOf(limit, 1), 200)
        return repository.findByStatusAfter(DeliveryStatus.EXHAUSTED, cursor, safeLimit)
            .map { DlqEntryView.from(it) }
    }

    @Transactional
    override fun replay(attemptId: UUID): DlqEntryView {
        requireAdmin()
        val attempt = loadOrThrow(attemptId)
        if (attempt.status != DeliveryStatus.EXHAUSTED) {
            throw IllegalDlqOperationException(
                "replay only allowed on EXHAUSTED, was ${attempt.status}",
            )
        }
        attempt.replayFromExhausted()
        val saved = repository.save(attempt)

        // outbox 재발행 — outbox relay 가 다음 polling 에서 Kafka topic 으로 push.
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
        log.info("DLQ replay attemptId={}", saved.id)
        return DlqEntryView.from(saved)
    }

    @Transactional
    override fun discard(attemptId: UUID, reason: String?): DlqEntryView {
        requireAdmin()
        val attempt = loadOrThrow(attemptId)
        if (attempt.status != DeliveryStatus.EXHAUSTED) {
            throw IllegalDlqOperationException(
                "discard only allowed on EXHAUSTED, was ${attempt.status}",
            )
        }
        attempt.discardFromExhausted(reason ?: "(no reason)")
        val saved = repository.save(attempt)

        auditLogger.log(
            "admin",
            "DLQ_DISCARD",
            mapOf(
                "attemptId" to saved.id.toString(),
                "notificationId" to saved.notificationId.toString(),
                "reason" to (reason ?: ""),
            ),
        )
        log.info("DLQ discard attemptId={} reason={}", saved.id, reason)
        return DlqEntryView.from(saved)
    }

    private fun loadOrThrow(id: UUID): DeliveryAttempt =
        repository.findById(id).orElseThrow { AttemptNotFoundException(id) }

    companion object {
        const val DELIVERY_TOPIC_PREFIX: String = "notification.delivery."

        private val log = LoggerFactory.getLogger(DlqAdminService::class.java)

        private fun requireAdmin() {
            if (!AdminContext.isAdmin()) {
                throw UnauthorizedAdminException("admin role required")
            }
        }
    }
}
