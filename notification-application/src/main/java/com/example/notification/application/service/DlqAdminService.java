package com.example.notification.application.service;

import com.example.notification.application.dto.DlqEntryView;
import com.example.notification.application.exception.AttemptNotFoundException;
import com.example.notification.application.exception.IllegalDlqOperationException;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.in.DlqAdminUseCase;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.application.security.AdminContext;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DLQ 운영 서비스. 모든 메서드가 admin role 가드 → audit log 기록.
 *
 * <p>replay 시 outbox 에 {@link DeliveryRequested} 재발행 — Kafka 에 다시 들어가 worker 가
 * 재처리. discard 는 DB 상태만 변경 (이벤트 발행 안 함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqAdminService implements DlqAdminUseCase {

    static final String DELIVERY_TOPIC_PREFIX = "notification.delivery.";

    private final DeliveryAttemptRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final AuditLogger auditLogger;

    @Override
    @Transactional(readOnly = true)
    public List<DlqEntryView> list(UUID cursor, int limit) {
        requireAdmin();
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return repository.findByStatusAfter(DeliveryStatus.EXHAUSTED, cursor, safeLimit).stream()
                .map(DlqEntryView::from)
                .toList();
    }

    @Override
    @Transactional
    public DlqEntryView replay(UUID attemptId) {
        requireAdmin();
        DeliveryAttempt attempt = loadOrThrow(attemptId);
        if (attempt.status() != DeliveryStatus.EXHAUSTED) {
            throw new IllegalDlqOperationException(
                    "replay only allowed on EXHAUSTED, was " + attempt.status());
        }
        attempt.replayFromExhausted();
        DeliveryAttempt saved = repository.save(attempt);

        // outbox 재발행 — outbox relay 가 다음 polling 에서 Kafka topic 으로 push.
        outboxPublisher.publish(
                DELIVERY_TOPIC_PREFIX + saved.channel().type().name().toLowerCase(),
                saved.id().toString(),
                DeliveryRequested.of(
                        saved.notificationId(), saved.id(), saved.channel().type()));

        auditLogger.log(
                "admin",
                "DLQ_REPLAY",
                Map.of(
                        "attemptId", saved.id().toString(),
                        "notificationId", saved.notificationId().toString(),
                        "channel", saved.channel().type().name()));
        log.info("DLQ replay attemptId={}", saved.id());
        return DlqEntryView.from(saved);
    }

    @Override
    @Transactional
    public DlqEntryView discard(UUID attemptId, String reason) {
        requireAdmin();
        DeliveryAttempt attempt = loadOrThrow(attemptId);
        if (attempt.status() != DeliveryStatus.EXHAUSTED) {
            throw new IllegalDlqOperationException(
                    "discard only allowed on EXHAUSTED, was " + attempt.status());
        }
        attempt.discardFromExhausted(reason == null ? "(no reason)" : reason);
        DeliveryAttempt saved = repository.save(attempt);

        auditLogger.log(
                "admin",
                "DLQ_DISCARD",
                Map.of(
                        "attemptId", saved.id().toString(),
                        "notificationId", saved.notificationId().toString(),
                        "reason", reason == null ? "" : reason));
        log.info("DLQ discard attemptId={} reason={}", saved.id(), reason);
        return DlqEntryView.from(saved);
    }

    private DeliveryAttempt loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AttemptNotFoundException(id));
    }

    private static void requireAdmin() {
        if (!AdminContext.isAdmin()) {
            throw new UnauthorizedAdminException("admin role required");
        }
    }
}
