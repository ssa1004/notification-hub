package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.port.`in`.RegisterTemplateUseCase
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.shared.Locale
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@JvmRecord
data class RegisterTemplateRequest(
    @field:NotBlank @field:Size(max = 128) val key: String,
    @field:NotBlank val locale: String,
    @field:NotNull val channelType: ChannelType?,
    @field:NotBlank @field:Size(max = 200) val titleTemplate: String,
    @field:NotBlank @field:Size(max = 4000) val bodyTemplate: String,
) {

    fun toCommand(): RegisterTemplateUseCase.RegisterCommand =
        RegisterTemplateUseCase.RegisterCommand(
            key, Locale(locale), channelType!!, titleTemplate, bodyTemplate,
        )
}
