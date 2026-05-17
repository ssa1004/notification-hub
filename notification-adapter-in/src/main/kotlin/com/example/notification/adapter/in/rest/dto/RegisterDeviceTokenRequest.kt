package com.example.notification.adapter.`in`.rest.dto

import com.example.notification.application.port.`in`.RegisterDeviceTokenUseCase
import com.example.notification.domain.device.DeviceToken
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@JvmRecord
data class RegisterDeviceTokenRequest(
    @field:NotBlank @field:Size(max = 128) val recipientId: String,
    @field:NotNull val platform: DeviceToken.Platform?,
    @field:NotBlank @field:Size(max = 256) val token: String,
) {

    fun toCommand(): RegisterDeviceTokenUseCase.RegisterCommand =
        RegisterDeviceTokenUseCase.RegisterCommand(recipientId, platform!!, token)
}
