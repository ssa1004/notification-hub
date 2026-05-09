package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.port.in.RegisterTemplateUseCase.RegisterCommand;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterTemplateRequest(
        @NotBlank @Size(max = 128) String key,
        @NotBlank String locale,
        @NotNull ChannelType channelType,
        @NotBlank @Size(max = 200) String titleTemplate,
        @NotBlank @Size(max = 4000) String bodyTemplate) {

    public RegisterCommand toCommand() {
        return new RegisterCommand(
                key, new Locale(locale), channelType, titleTemplate, bodyTemplate);
    }
}
