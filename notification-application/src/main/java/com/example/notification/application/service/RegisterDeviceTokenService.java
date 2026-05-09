package com.example.notification.application.service;

import com.example.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.example.notification.application.port.out.DeviceTokenRepository;
import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.recipient.RecipientId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterDeviceTokenService implements RegisterDeviceTokenUseCase {

    private final DeviceTokenRepository repository;

    @Override
    @Transactional
    public DeviceToken register(RegisterCommand command) {
        return repository
                .findByToken(command.token())
                .orElseGet(
                        () -> repository.save(
                                DeviceToken.register(
                                        new RecipientId(command.recipientId()),
                                        command.platform(),
                                        command.token())));
    }
}
