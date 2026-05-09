package com.example.notification.adapter.out.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryDispatcherTest {

    @Mock DeliveryAttemptRepository repository;
    @Mock DeliveryGateway pushGateway;

    DeliveryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(pushGateway.channelType()).thenReturn(ChannelType.PUSH);
        dispatcher = new DeliveryDispatcher(repository, new DeliveryGatewayRouter(List.of(pushGateway)));
    }

    @Test
    void successful_dispatch_marks_succeeded() {
        DeliveryAttempt a = newPushAttempt();
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushGateway.dispatch(any())).thenReturn("vendor-1");

        dispatcher.dispatch(a.id());
        assertThat(a.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    }

    @Test
    void transient_failure_marks_failed_and_re_pending() {
        DeliveryAttempt a = newPushAttempt();
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushGateway.dispatch(any())).thenThrow(new VendorTransientException("5xx"));

        dispatcher.dispatch(a.id());
        assertThat(a.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(a.retryCount()).isEqualTo(1);
        assertThat(a.failureReason()).contains("transient");
    }

    @Test
    void already_final_attempt_skipped() {
        DeliveryAttempt a = newPushAttempt();
        a.markDispatching();
        a.markSucceeded("vendor-x");
        when(repository.findById(a.id())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.id());
        verify(pushGateway, never()).dispatch(any());
        verify(repository, never()).save(any());
    }

    @Test
    void unknown_attempt_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.dispatch(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DeliveryAttempt newPushAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.PUSH, "p".repeat(160)),
                "title",
                "body");
    }
}
