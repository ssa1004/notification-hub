package com.example.notification.adapter.`in`.rest

import com.example.notification.adapter.`in`.rest.dto.RegisterDeviceTokenRequest
import com.example.notification.application.port.`in`.RegisterDeviceTokenUseCase
import com.example.notification.domain.device.DeviceToken
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/devices")
class DeviceTokenController(
    private val registerUseCase: RegisterDeviceTokenUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ): ResponseEntity<DeviceTokenResponse> {
        val d = registerUseCase.register(request.toCommand())
        return ResponseEntity.ok(DeviceTokenResponse.from(d))
    }

    @JvmRecord
    data class DeviceTokenResponse(
        val id: UUID,
        val recipientId: String,
        val platform: String,
        val active: Boolean,
    ) {
        companion object {
            @JvmStatic
            fun from(d: DeviceToken): DeviceTokenResponse =
                DeviceTokenResponse(
                    d.id,
                    d.recipientId.value,
                    d.platform.name,
                    d.isActive(),
                )
        }
    }
}
