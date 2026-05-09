package com.example.notification.domain.notification;

import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.IdempotencyKey;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 알림 aggregate root. 한 발송 요청 = 한 Notification.
 *
 * <p>한 Notification 이 여러 {@code DeliveryAttempt} 로 fan-out 되며, 각 attempt 가 채널별로
 * 독립적으로 retry / DLQ / 성공 처리됩니다. Notification 자체는 fan-out 이 끝났는지만 추적.
 *
 * <p>payload 는 템플릿 placeholder 의 변수 (예: {@code {name=홍길동, amount=10000}}) 또는
 * 자유 메타 데이터. 템플릿 미사용 알림은 title/body 가 그대로 사용됨.
 */
public final class Notification {

    private final UUID id;
    private final IdempotencyKey idempotencyKey;
    private final RecipientId recipientId;
    private final NotificationKind kind;
    private final String title;
    private final String body;
    private final Map<String, String> payload;
    private final String templateKey; // null 이면 raw 발송
    private final Instant createdAt;
    private NotificationStatus status;

    public Notification(
            UUID id,
            IdempotencyKey idempotencyKey,
            RecipientId recipientId,
            NotificationKind kind,
            String title,
            String body,
            Map<String, String> payload,
            String templateKey,
            Instant createdAt,
            NotificationStatus status) {
        this.id = Objects.requireNonNull(id);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.recipientId = Objects.requireNonNull(recipientId);
        this.kind = Objects.requireNonNull(kind);
        this.title = validateTitle(title);
        this.body = validateBody(body);
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.templateKey = templateKey;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = Objects.requireNonNull(status);
    }

    /** 새 Notification 을 ACCEPTED 상태로 생성. */
    public static Notification accept(
            IdempotencyKey idempotencyKey,
            RecipientId recipientId,
            NotificationKind kind,
            String title,
            String body,
            Map<String, String> payload,
            String templateKey) {
        return new Notification(
                UUID.randomUUID(),
                idempotencyKey,
                recipientId,
                kind,
                title,
                body,
                payload == null ? new HashMap<>() : new HashMap<>(payload),
                templateKey,
                Instant.now(),
                NotificationStatus.ACCEPTED);
    }

    private static String validateTitle(String title) {
        Objects.requireNonNull(title, "title must not be null");
        if (title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title length 1..200 required");
        }
        return title;
    }

    private static String validateBody(String body) {
        Objects.requireNonNull(body, "body must not be null");
        if (body.isBlank() || body.length() > 4000) {
            throw new IllegalArgumentException("body length 1..4000 required");
        }
        return body;
    }

    /** ACCEPTED → FANNED_OUT 전이. */
    public void markFannedOut() {
        if (status != NotificationStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "fan-out only allowed from ACCEPTED, was " + status);
        }
        this.status = NotificationStatus.FANNED_OUT;
    }

    /** ACCEPTED → SUPPRESSED 전이 (발송 채널 0개). */
    public void markSuppressed() {
        if (status != NotificationStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "suppress only allowed from ACCEPTED, was " + status);
        }
        this.status = NotificationStatus.SUPPRESSED;
    }

    /** FANNED_OUT → COMPLETED 전이 (모든 attempt 가 final). */
    public void markCompleted() {
        if (status != NotificationStatus.FANNED_OUT) {
            throw new IllegalStateException(
                    "complete only allowed from FANNED_OUT, was " + status);
        }
        this.status = NotificationStatus.COMPLETED;
    }

    public UUID id() {
        return id;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public RecipientId recipientId() {
        return recipientId;
    }

    public NotificationKind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Map<String, String> payload() {
        return Collections.unmodifiableMap(payload);
    }

    public String templateKey() {
        return templateKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public NotificationStatus status() {
        return status;
    }
}
