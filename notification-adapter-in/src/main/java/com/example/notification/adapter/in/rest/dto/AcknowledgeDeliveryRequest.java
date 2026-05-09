package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase.AcknowledgeCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AcknowledgeDeliveryRequest(
        @NotNull Boolean success,
        String vendorMessageId,
        String failureReason) {

    public AcknowledgeCommand toCommand(UUID deliveryAttemptId) {
        return new AcknowledgeCommand(deliveryAttemptId, success, vendorMessageId, failureReason);
    }
}
