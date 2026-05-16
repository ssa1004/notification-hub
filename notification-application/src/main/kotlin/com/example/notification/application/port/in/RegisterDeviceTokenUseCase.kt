package com.example.notification.application.port.`in`

import com.example.notification.domain.device.DeviceToken

/**
 * 모바일 앱이 자기 FCM/APNs token 을 등록. 이미 같은 raw token 이 있으면 기존 row 그대로 반환.
 */
interface RegisterDeviceTokenUseCase {

    fun register(command: RegisterCommand): DeviceToken

    @JvmRecord
    data class RegisterCommand(
        val recipientId: String,
        val platform: DeviceToken.Platform,
        val token: String,
    )
}
