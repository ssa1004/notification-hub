package com.example.notification.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.notification.adapter.out.persistence.JpaRecipientRepository;
import com.example.notification.adapter.out.persistence.repository.DeliveryAttemptJpaRepository;
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.example.notification.application.dto.SendNotificationCommand;
import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.device.DeviceToken.Platform;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryStatus;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.notification.NotificationStatus;
import com.example.notification.domain.recipient.Recipient;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.Locale;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SendNotificationE2ETest extends AbstractE2ETest {

    @Autowired SendNotificationUseCase sendUseCase;
    @Autowired RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    @Autowired JpaRecipientRepository recipientRepository;
    @Autowired DeliveryAttemptJpaRepository attemptJpa;
    @Autowired OutboxEventJpaRepository outboxJpa;

    private static final RecipientId USER = new RecipientId("e2e-user-" + UUID.randomUUID());

    @BeforeEach
    void seed() {
        recipientRepository.save(
                new Recipient(
                        USER,
                        List.of(
                                new Channel(ChannelType.PUSH, "p".repeat(160)),
                                new Channel(ChannelType.EMAIL, "user@example.com"),
                                new Channel(ChannelType.SMS, "+821012345678")),
                        Locale.KO_KR,
                        ZoneId.of("Asia/Seoul")));
        // multi-device fan-out 정책 — PUSH 는 active device token 이 있어야 발송 대상.
        // 기기 등록 1개로 PUSH 1개 발송 보장.
        registerDeviceTokenUseCase.register(
                new RegisterDeviceTokenUseCase.RegisterCommand(
                        USER.value(), Platform.IOS, "p".repeat(160)));
    }

    @Test
    void send_then_outbox_drained_then_attempts_succeed() {
        String idem = "idem-" + UUID.randomUUID();
        SendNotificationResult result = sendUseCase.send(
                new SendNotificationCommand(
                        idem,
                        USER.value(),
                        NotificationKind.SECURITY,
                        "OTP",
                        "코드: 123456",
                        Map.of(),
                        null));

        assertThat(result.status()).isEqualTo(NotificationStatus.FANNED_OUT);
        assertThat(result.dispatchedChannels())
                .containsExactly(ChannelType.PUSH, ChannelType.EMAIL, ChannelType.SMS);

        // attempt 3개 PENDING → outbox relay → kafka consumer → DispatchDeliveryUseCase → SUCCEEDED
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<?> attempts = attemptJpa.findByNotificationId(result.notificationId());
            assertThat(attempts).hasSize(3);
            attempts.forEach(
                    a -> assertThat(((com.example.notification.adapter.out.persistence.entity
                                            .DeliveryAttemptEntity) a)
                                    .getStatus())
                            .isEqualTo(DeliveryStatus.SUCCEEDED));
        });

        // outbox row 가 모두 PUBLISHED 가 됨 (notificationFannedOut 1 + delivery * 3 = 4)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            long pending = outboxJpa.findAll().stream()
                    .filter(e -> "PENDING".equals(e.getStatus()))
                    .count();
            assertThat(pending).isZero();
        });
    }

    @Test
    void duplicate_idempotency_key_throws() {
        String idem = "idem-dup-" + UUID.randomUUID();
        sendUseCase.send(
                new SendNotificationCommand(
                        idem,
                        USER.value(),
                        NotificationKind.SECURITY,
                        "OTP",
                        "코드: 1",
                        Map.of(),
                        null));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> sendUseCase.send(
                                new SendNotificationCommand(
                                        idem,
                                        USER.value(),
                                        NotificationKind.SECURITY,
                                        "OTP",
                                        "코드: 2",
                                        Map.of(),
                                        null)))
                .isInstanceOf(DuplicateRequestException.class);
    }
}
