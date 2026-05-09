package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.application.port.out.DeviceTokenRepository;
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
class DispatchDeliveryServiceTest {

    @Mock DeliveryAttemptRepository repository;
    @Mock DeviceTokenRepository deviceTokenRepository;
    @Mock DeliveryGateway pushGateway;

    DispatchDeliveryService service;

    @BeforeEach
    void setUp() {
        when(pushGateway.channelType()).thenReturn(ChannelType.PUSH);
        service =
                new DispatchDeliveryService(
                        repository, deviceTokenRepository, List.of(pushGateway));
    }

    @Test
    void duplicate_gateway_for_same_channel_rejected_at_construction() {
        DeliveryGateway p1 = mock(ChannelType.PUSH);
        DeliveryGateway p2 = mock(ChannelType.PUSH);
        assertThatThrownBy(
                        () ->
                                new DispatchDeliveryService(
                                        repository, deviceTokenRepository, List.of(p1, p2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void PUSH_영구_실패_시_device_token_비활성화() {
        DeliveryAttempt a = newPushAttempt();
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushGateway.dispatch(any()))
                .thenThrow(
                        new com.example.notification.adapter.out.vendor.VendorPermanentExceptionStub(
                                "FCM NOT_REGISTERED"));

        service.dispatch(a.id());

        verify(deviceTokenRepository).deactivateByToken(a.channel().address());
        assertThat(a.failureReason()).contains("permanent");
    }

    @Test
    void EMAIL_영구_실패_시_device_token_비활성화_안_함() {
        DeliveryGateway emailGateway =
                new DeliveryGateway() {
                    @Override
                    public ChannelType channelType() {
                        return ChannelType.EMAIL;
                    }

                    @Override
                    public String dispatch(DeliveryAttempt attempt) {
                        throw new com.example.notification.adapter.out.vendor.VendorPermanentExceptionStub(
                                "SES MessageRejected");
                    }
                };
        DispatchDeliveryService emailService =
                new DispatchDeliveryService(
                        repository, deviceTokenRepository, List.of(emailGateway));
        DeliveryAttempt a =
                DeliveryAttempt.create(
                        UUID.randomUUID(),
                        new Channel(ChannelType.EMAIL, "u@example.com"),
                        "title",
                        "body");
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        emailService.dispatch(a.id());

        verify(deviceTokenRepository, never()).deactivateByToken(any());
    }

    @Test
    void successful_dispatch_marks_succeeded() {
        DeliveryAttempt a = newPushAttempt();
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushGateway.dispatch(any())).thenReturn("vendor-1");

        service.dispatch(a.id());
        assertThat(a.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(a.vendorMessageId()).isEqualTo("vendor-1");
    }

    @Test
    void transient_failure_marks_failed_and_re_pending() {
        DeliveryAttempt a = newPushAttempt();
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushGateway.dispatch(any())).thenThrow(new RuntimeException("5xx transient"));

        service.dispatch(a.id());
        assertThat(a.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(a.failureReason()).contains("transient");
        assertThat(a.retryCount()).isEqualTo(1);
    }

    @Test
    void already_final_attempt_skipped() {
        DeliveryAttempt a = newPushAttempt();
        a.markDispatching();
        a.markSucceeded("vendor-x");
        when(repository.findById(a.id())).thenReturn(Optional.of(a));

        service.dispatch(a.id());
        verify(pushGateway, never()).dispatch(any());
        verify(repository, never()).save(any());
    }

    @Test
    void unknown_attempt_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.dispatch(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void no_gateway_marks_failed() {
        // SMS gateway 없는 상태에서 SMS attempt 들어오면
        DeliveryAttempt a = DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.SMS, "+821012345678"),
                "title",
                "body");
        when(repository.findById(a.id())).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.dispatch(a.id());
        assertThat(a.status()).isEqualTo(DeliveryStatus.PENDING); // first failure → pending
        assertThat(a.failureReason()).contains("no gateway");
    }

    private static DeliveryGateway mock(ChannelType type) {
        return new DeliveryGateway() {
            @Override
            public ChannelType channelType() {
                return type;
            }

            @Override
            public String dispatch(DeliveryAttempt attempt) {
                return "x";
            }
        };
    }

    private static DeliveryAttempt newPushAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.PUSH, "p".repeat(160)),
                "title",
                "body");
    }
}
