package com.example.notification.application.service

import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase
import com.example.notification.application.port.`in`.AcknowledgeDeliveryUseCase.AcknowledgeCommand
import com.example.notification.application.port.out.AuditLogger
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * vendor 콜백 처리. 콜백이 늦게 와서 attempt 가 이미 EXHAUSTED 거나 SUCCEEDED 일 수 있으므로
 * idempotent 하게 처리합니다 — 같은 결과면 무시, 다른 결과면 audit 만 남기고 보존.
 */
@Service
class AcknowledgeDeliveryService(
    private val repository: DeliveryAttemptRepository,
    private val auditLogger: AuditLogger,
) : AcknowledgeDeliveryUseCase {

    @Transactional
    override fun acknowledge(command: AcknowledgeCommand) {
        val attempt: DeliveryAttempt = repository
            .findById(command.deliveryAttemptId)
            .orElseThrow {
                IllegalArgumentException("deliveryAttempt not found: ${command.deliveryAttemptId}")
            }

        if (attempt.isFinal()) {
            log.info(
                "ack on already-final attempt id={} status={} ignoredCallback={}",
                attempt.id,
                attempt.status,
                command.success,
            )
            auditLog(attempt, command, "IGNORED_FINAL")
            return
        }

        if (attempt.status == DeliveryStatus.PENDING) {
            // worker 가 아직 dispatch 도 안 했는데 콜백이 먼저 — 비정상이지만 일단 dispatch 처리.
            attempt.markDispatching()
        }

        if (command.success) {
            attempt.markSucceeded(command.vendorMessageId)
        } else {
            attempt.markFailed(command.failureReason ?: "vendor callback")
        }
        repository.save(attempt)
        auditLog(attempt, command, "APPLIED")
    }

    private fun auditLog(attempt: DeliveryAttempt, command: AcknowledgeCommand, outcome: String) {
        val data = mutableMapOf<String, Any>()
        data["attemptId"] = attempt.id.toString()
        data["outcome"] = outcome
        data["status"] = attempt.status.name
        data["success"] = command.success
        command.vendorMessageId?.let { data["vendorMessageId"] = it }
        auditLogger.log("vendor", "DELIVERY_ACK", data)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AcknowledgeDeliveryService::class.java)
    }
}
