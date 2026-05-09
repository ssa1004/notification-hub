package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase.AcknowledgeCommand;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcknowledgeDeliveryServiceTest {

    @Mock DeliveryAttemptRepository repository;
    @Mock AuditLogger auditLogger;

    AcknowledgeDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new AcknowledgeDeliveryService(repository, auditLogger);
    }

    @Test
    void success_ack_marks_succeeded() {
        DeliveryAttempt attempt = newAttempt();
        attempt.markDispatching();
        when(repository.findById(attempt.id())).thenReturn(Optional.of(attempt));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acknowledge(new AcknowledgeCommand(attempt.id(), true, "vendor-1", null));
        assertThat(attempt.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        verify(auditLogger).log(any(), any(), any());
    }

    @Test
    void failure_ack_increments_retry_until_exhausted() {
        DeliveryAttempt attempt = newAttempt();
        attempt.markDispatching();
        when(repository.findById(attempt.id())).thenReturn(Optional.of(attempt));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acknowledge(new AcknowledgeCommand(attempt.id(), false, null, "vendor down"));
        assertThat(attempt.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(attempt.retryCount()).isEqualTo(1);
    }

    @Test
    void unknown_attempt_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(
                        () -> service.acknowledge(
                                new AcknowledgeCommand(id, true, "x", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ack_on_already_final_attempt_is_ignored() {
        DeliveryAttempt attempt = newAttempt();
        attempt.markDispatching();
        attempt.markSucceeded("v-1");
        when(repository.findById(attempt.id())).thenReturn(Optional.of(attempt));

        service.acknowledge(new AcknowledgeCommand(attempt.id(), false, null, "late callback"));
        assertThat(attempt.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        verify(repository, never()).save(any());
    }

    private static DeliveryAttempt newAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.EMAIL, "user@example.com"),
                "title",
                "body");
    }
}
