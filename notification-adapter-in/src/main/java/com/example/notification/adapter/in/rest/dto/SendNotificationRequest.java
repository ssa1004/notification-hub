package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.dto.SendNotificationCommand;
import com.example.notification.domain.notification.NotificationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SendNotificationRequest(
        @NotBlank @Size(max = 128) String recipientId,
        @NotNull NotificationKind kind,
        @Size(max = 200) String title,
        @Size(max = 4000) String body,
        Map<String, String> payload,
        @Size(max = 128) String templateKey) {

    public SendNotificationCommand toCommand(String idempotencyKey) {
        return new SendNotificationCommand(
                idempotencyKey, recipientId, kind, title, body, payload, templateKey);
    }
}
