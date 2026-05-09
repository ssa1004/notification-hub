package com.example.notification.application.dto;

import com.example.notification.domain.notification.NotificationKind;
import java.util.Map;

/**
 * SendNotificationUseCase 입력. 템플릿 사용 시 {@code templateKey} + {@code payload} (변수)
 * 만 채우고 title/body 는 비울 수 있음 — 그러면 템플릿 본문이 사용됨.
 *
 * <p>raw 발송 (템플릿 미사용) 은 templateKey=null + title/body 직접 입력.
 */
public record SendNotificationCommand(
        String idempotencyKey,
        String recipientId,
        NotificationKind kind,
        String title,
        String body,
        Map<String, String> payload,
        String templateKey) {

    public SendNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey required");
        }
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind required");
        }
        if ((title == null || title.isBlank()) && (templateKey == null || templateKey.isBlank())) {
            throw new IllegalArgumentException("either title/body or templateKey required");
        }
        if (payload == null) {
            payload = Map.of();
        }
    }
}
