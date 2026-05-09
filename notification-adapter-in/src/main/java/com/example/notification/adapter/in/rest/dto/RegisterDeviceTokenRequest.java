package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.port.in.RegisterDeviceTokenUseCase.RegisterCommand;
import com.example.notification.domain.device.DeviceToken.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceTokenRequest(
        @NotBlank @Size(max = 128) String recipientId,
        @NotNull Platform platform,
        @NotBlank @Size(max = 256) String token) {

    public RegisterCommand toCommand() {
        return new RegisterCommand(recipientId, platform, token);
    }
}
