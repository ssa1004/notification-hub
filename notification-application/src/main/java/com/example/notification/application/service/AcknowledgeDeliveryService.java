package com.example.notification.application.service;

import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * vendor 콜백 처리. 콜백이 늦게 와서 attempt 가 이미 EXHAUSTED 거나 SUCCEEDED 일 수 있으므로
 * idempotent 하게 처리합니다 — 같은 결과면 무시, 다른 결과면 audit 만 남기고 보존.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcknowledgeDeliveryService implements AcknowledgeDeliveryUseCase {

    private final DeliveryAttemptRepository repository;
    private final AuditLogger auditLogger;

    @Override
    @Transactional
    public void acknowledge(AcknowledgeCommand command) {
        DeliveryAttempt attempt = repository
                .findById(command.deliveryAttemptId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "deliveryAttempt not found: " + command.deliveryAttemptId()));

        if (attempt.isFinal()) {
            log.info(
                    "ack on already-final attempt id={} status={} ignoredCallback={}",
                    attempt.id(),
                    attempt.status(),
                    command.success());
            auditLog(attempt, command, "IGNORED_FINAL");
            return;
        }

        if (attempt.status() == DeliveryStatus.PENDING) {
            // worker 가 아직 dispatch 도 안 했는데 콜백이 먼저 — 비정상이지만 일단 dispatch 처리.
            attempt.markDispatching();
        }

        if (command.success()) {
            attempt.markSucceeded(command.vendorMessageId());
        } else {
            attempt.markFailed(
                    command.failureReason() == null ? "vendor callback" : command.failureReason());
        }
        repository.save(attempt);
        auditLog(attempt, command, "APPLIED");
    }

    private void auditLog(DeliveryAttempt attempt, AcknowledgeCommand command, String outcome) {
        Map<String, Object> data = new HashMap<>();
        data.put("attemptId", attempt.id().toString());
        data.put("outcome", outcome);
        data.put("status", attempt.status().name());
        data.put("success", command.success());
        if (command.vendorMessageId() != null) {
            data.put("vendorMessageId", command.vendorMessageId());
        }
        auditLogger.log("vendor", "DELIVERY_ACK", data);
    }
}
