package com.example.notification.application.service

import com.example.notification.application.port.`in`.RegisterDeviceTokenUseCase
import com.example.notification.application.port.`in`.RegisterDeviceTokenUseCase.RegisterCommand
import com.example.notification.application.port.out.DeviceTokenRepository
import com.example.notification.domain.device.DeviceToken
import com.example.notification.domain.recipient.RecipientId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterDeviceTokenService(
    private val repository: DeviceTokenRepository,
) : RegisterDeviceTokenUseCase {

    @Transactional
    override fun register(command: RegisterCommand): DeviceToken =
        repository.findByToken(command.token).orElseGet {
            repository.save(
                DeviceToken.register(
                    RecipientId(command.recipientId),
                    command.platform,
                    command.token,
                ),
            )
        }
}
