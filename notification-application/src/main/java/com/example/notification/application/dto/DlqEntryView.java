package com.example.notification.application.dto;

import com.example.notification.domain.delivery.DeliveryAttempt;
import java.time.Instant;
import java.util.UUID;

/** DLQ 운영 화면 1줄. 본문은 길이만 보여주고 vendor message id / failure reason 등 진단 메타. */
public record DlqEntryView(
        UUID attemptId,
        UUID notificationId,
        String channelType,
        String channelAddressMasked,
        String status,
        int retryCount,
        Instant createdAt,
        Instant completedAt,
        String failureReason,
        int renderedBodyLength) {

    public static DlqEntryView from(DeliveryAttempt a) {
        return new DlqEntryView(
                a.id(),
                a.notificationId(),
                a.channel().type().name(),
                a.channel().toString(),
                a.status().name(),
                a.retryCount(),
                a.createdAt(),
                a.completedAt(),
                a.failureReason(),
                a.renderedBody() == null ? 0 : a.renderedBody().length());
    }
}
