package com.example.notification.domain.delivery;

import com.example.notification.domain.channel.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 한 알림이 한 채널로 발송 시도되는 단위. notification 1 : N attempt.
 *
 * <p>retry 횟수 / 다음 시도 시각 / 최종 실패 사유 등 vendor 호출 라이프사이클을 담습니다.
 *
 * <p>{@link #renderedTitle} / {@link #renderedBody} 는 템플릿 + payload 변수 치환이 끝난
 * 최종 텍스트입니다. attempt 단위로 보관하는 이유: 같은 알림이라도 채널별로 본문이 달라지고
 * (SMS 90B 제한, 이메일 풀 본문) audit / 재현에 필요.
 */
public final class DeliveryAttempt {

    /** Resilience4j retry 상한과 정합. config 변경 시 같이 바뀜. */
    public static final int MAX_RETRY = 5;

    private final UUID id;
    private final UUID notificationId;
    private final Channel channel;
    private final String renderedTitle;
    private final String renderedBody;
    private final Instant createdAt;
    private DeliveryStatus status;
    private int retryCount;
    private Instant nextAttemptAt;
    private Instant completedAt;
    private String vendorMessageId;
    private String failureReason;

    public DeliveryAttempt(
            UUID id,
            UUID notificationId,
            Channel channel,
            String renderedTitle,
            String renderedBody,
            Instant createdAt,
            DeliveryStatus status,
            int retryCount,
            Instant nextAttemptAt,
            Instant completedAt,
            String vendorMessageId,
            String failureReason) {
        this.id = Objects.requireNonNull(id);
        this.notificationId = Objects.requireNonNull(notificationId);
        this.channel = Objects.requireNonNull(channel);
        this.renderedTitle = Objects.requireNonNull(renderedTitle);
        this.renderedBody = Objects.requireNonNull(renderedBody);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = Objects.requireNonNull(status);
        this.retryCount = retryCount;
        this.nextAttemptAt = nextAttemptAt;
        this.completedAt = completedAt;
        this.vendorMessageId = vendorMessageId;
        this.failureReason = failureReason;
    }

    /** 새 PENDING attempt 생성. */
    public static DeliveryAttempt create(
            UUID notificationId,
            Channel channel,
            String renderedTitle,
            String renderedBody) {
        Instant now = Instant.now();
        return new DeliveryAttempt(
                UUID.randomUUID(),
                notificationId,
                channel,
                renderedTitle,
                renderedBody,
                now,
                DeliveryStatus.PENDING,
                0,
                now, // 즉시 발송 가능
                null,
                null,
                null);
    }

    /** Worker 가 vendor 호출 직전 호출. PENDING → DISPATCHING. */
    public void markDispatching() {
        if (status != DeliveryStatus.PENDING) {
            throw new IllegalStateException(
                    "dispatch only allowed from PENDING, was " + status);
        }
        this.status = DeliveryStatus.DISPATCHING;
    }

    /** vendor 호출 성공. */
    public void markSucceeded(String vendorMessageId) {
        if (status != DeliveryStatus.DISPATCHING) {
            throw new IllegalStateException(
                    "succeed only allowed from DISPATCHING, was " + status);
        }
        this.status = DeliveryStatus.SUCCEEDED;
        this.completedAt = Instant.now();
        this.vendorMessageId = vendorMessageId;
    }

    /**
     * vendor 호출 실패. retry 한도 안이면 PENDING + 다음 시도 시각 계산. 초과면 EXHAUSTED.
     */
    public void markFailed(String reason) {
        if (status != DeliveryStatus.DISPATCHING) {
            throw new IllegalStateException(
                    "fail only allowed from DISPATCHING, was " + status);
        }
        this.failureReason = reason;
        if (retryCount + 1 >= MAX_RETRY) {
            this.status = DeliveryStatus.EXHAUSTED;
            this.completedAt = Instant.now();
        } else {
            this.retryCount++;
            this.status = DeliveryStatus.PENDING;
            this.nextAttemptAt = Instant.now().plus(backoffFor(retryCount));
        }
    }

    /**
     * Exponential backoff with jitter. base 1s, factor 2, jitter ±25%, cap 60s.
     *
     * <p>retry=1 → 1s, retry=2 → 2s, retry=3 → 4s, retry=4 → 8s, retry=5 → 16s (cap 적용)
     */
    static Duration backoffFor(int retry) {
        long baseMs = (long) (1000L * Math.pow(2, retry - 1));
        long capped = Math.min(baseMs, 60_000L);
        // 결정성: jitter 는 worker 의 sleep 단계에서 적용. 도메인은 base 만 보장.
        return Duration.ofMillis(capped);
    }

    public boolean isFinal() {
        return status == DeliveryStatus.SUCCEEDED
                || status == DeliveryStatus.EXHAUSTED;
    }

    public UUID id() {
        return id;
    }

    public UUID notificationId() {
        return notificationId;
    }

    public Channel channel() {
        return channel;
    }

    public String renderedTitle() {
        return renderedTitle;
    }

    public String renderedBody() {
        return renderedBody;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public DeliveryStatus status() {
        return status;
    }

    public int retryCount() {
        return retryCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String vendorMessageId() {
        return vendorMessageId;
    }

    public String failureReason() {
        return failureReason;
    }
}
