package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.port.in.RegisterDeviceTokenUseCase.RegisterCommand;
import com.example.notification.application.port.out.DeviceTokenRepository;
import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.device.DeviceToken.Platform;
import com.example.notification.domain.recipient.RecipientId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterDeviceTokenServiceTest {

    @Mock DeviceTokenRepository repository;
    RegisterDeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new RegisterDeviceTokenService(repository);
    }

    @Test
    void new_token_persisted() {
        String token = "t".repeat(160);
        when(repository.findByToken(token)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceToken result = service.register(
                new RegisterCommand("u-1", Platform.ANDROID, token));
        assertThat(result.token()).isEqualTo(token);
        assertThat(result.isActive()).isTrue();
        verify(repository).save(any());
    }

    @Test
    void existing_token_returned_without_save() {
        String token = "t".repeat(160);
        DeviceToken existing = DeviceToken.register(
                new RecipientId("u-1"), Platform.ANDROID, token);
        when(repository.findByToken(token)).thenReturn(Optional.of(existing));

        DeviceToken result = service.register(
                new RegisterCommand("u-1", Platform.ANDROID, token));
        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
    }
}
