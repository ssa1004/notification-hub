package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.dto.SendNotificationCommand;
import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.exception.RecipientNotFoundException;
import com.example.notification.application.exception.TemplateNotFoundException;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.IdempotencyStore;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.application.port.out.RateLimiter;
import com.example.notification.application.port.out.RecipientRepository;
import com.example.notification.application.port.out.TemplateRenderer;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.application.port.out.UserPreferenceRepository;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.notification.Notification;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.notification.NotificationStatus;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.Recipient;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.shared.RateLimitDecision;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendNotificationServiceTest {

    @Mock IdempotencyStore idempotencyStore;
    @Mock NotificationRepository notificationRepository;
    @Mock DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock RecipientRepository recipientRepository;
    @Mock UserPreferenceRepository userPreferenceRepository;
    @Mock TemplateRepository templateRepository;
    @Mock TemplateRenderer templateRenderer;
    @Mock RateLimiter rateLimiter;
    @Mock OutboxPublisher outboxPublisher;
    @Mock AuditLogger auditLogger;

    SendNotificationService service;

    private static final RecipientId USER = new RecipientId("u-1");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        service = new SendNotificationService(
                idempotencyStore,
                notificationRepository,
                deliveryAttemptRepository,
                recipientRepository,
                userPreferenceRepository,
                templateRepository,
                templateRenderer,
                rateLimiter,
                outboxPublisher,
                new ChannelResolver(),
                auditLogger);
    }

    @Test
    void duplicate_request_throws() {
        when(idempotencyStore.tryAcquire(any(), eq(SendNotificationService.IDEM_TTL)))
                .thenReturn(false);
        assertThatThrownBy(() -> service.send(rawCmd()))
                .isInstanceOf(DuplicateRequestException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void recipient_not_found_throws() {
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(rawCmd()))
                .isInstanceOf(RecipientNotFoundException.class);
    }

    @Test
    void opt_out_returns_SUPPRESSED_and_does_not_publish() {
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.of(recipient()));
        when(userPreferenceRepository.findByRecipientId(USER))
                .thenReturn(
                        Optional.of(
                                UserPreference.defaults(USER)
                                        .withChannelOptOut(NotificationKind.MARKETING, false)));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SendNotificationResult result = service.send(rawCmd());
        assertThat(result.status()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(result.dispatchedChannels()).isEmpty();
        assertThat(result.suppressionReason()).isEqualTo("OPT_OUT");
        verify(outboxPublisher, never()).publish(any(), any(), any());
        verify(rateLimiter, never()).tryConsume(any(), any());
    }

    @Test
    void rate_limit_exceeded_aborts_fan_out() {
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.of(recipient()));
        when(userPreferenceRepository.findByRecipientId(USER))
                .thenReturn(Optional.of(UserPreference.defaults(USER)));
        when(rateLimiter.tryConsume(eq(USER), any()))
                .thenReturn(RateLimitDecision.deny(60_000));

        assertThatThrownBy(() -> service.send(rawCmdSecurity()))
                .isInstanceOf(RateLimitExceededException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void successful_fanout_persists_and_publishes() {
        Recipient r = recipient();
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.of(r));
        when(userPreferenceRepository.findByRecipientId(USER))
                .thenReturn(Optional.of(UserPreference.defaults(USER)));
        when(rateLimiter.tryConsume(eq(USER), any())).thenReturn(RateLimitDecision.allow(9));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryAttemptRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SendNotificationResult result = service.send(rawCmdSecurity());

        assertThat(result.status()).isEqualTo(NotificationStatus.FANNED_OUT);
        assertThat(result.dispatchedChannels())
                .containsExactly(ChannelType.PUSH, ChannelType.EMAIL, ChannelType.SMS);
        verify(outboxPublisher).publish(eq("notification.fanned-out"), any(), any());
        verify(outboxPublisher, atLeastOnce())
                .publish(startsWith("notification.delivery."), any(), any());
        verify(auditLogger, times(1))
                .log(eq(USER.value()), eq("NOTIFICATION_FANNED_OUT"), any());
    }

    @Test
    void template_missing_throws() {
        Recipient r = recipient();
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.of(r));
        when(userPreferenceRepository.findByRecipientId(USER))
                .thenReturn(Optional.of(UserPreference.defaults(USER)));
        when(rateLimiter.tryConsume(eq(USER), any())).thenReturn(RateLimitDecision.allow(9));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.findWithFallback(any(), any(), any()))
                .thenReturn(Optional.empty());

        SendNotificationCommand cmd = new SendNotificationCommand(
                "idem-tpl",
                USER.value(),
                NotificationKind.SECURITY,
                null,
                null,
                Map.of("code", "111"),
                "auth.otp.v1");
        assertThatThrownBy(() -> service.send(cmd))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void template_rendering_used_when_templateKey_provided() {
        Recipient r = recipient();
        when(idempotencyStore.tryAcquire(any(), any())).thenReturn(true);
        when(recipientRepository.findById(USER)).thenReturn(Optional.of(r));
        when(userPreferenceRepository.findByRecipientId(USER))
                .thenReturn(Optional.of(UserPreference.defaults(USER)));
        when(rateLimiter.tryConsume(eq(USER), any())).thenReturn(RateLimitDecision.allow(9));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryAttemptRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        Template tpl = Template.register(
                new TemplateKey("auth.otp.v1"),
                Locale.KO_KR,
                ChannelType.PUSH,
                "OTP",
                "코드: {code}");
        when(templateRepository.findWithFallback(any(), any(), any()))
                .thenReturn(Optional.of(tpl));
        when(templateRenderer.render(any(), any()))
                .thenReturn(new TemplateRenderer.Rendered("OTP", "코드: 111"));

        service.send(
                new SendNotificationCommand(
                        "idem-tpl-ok",
                        USER.value(),
                        NotificationKind.SECURITY,
                        null,
                        null,
                        Map.of("code", "111"),
                        "auth.otp.v1"));

        verify(templateRenderer, atLeastOnce()).render(any(), any());
    }

    private static SendNotificationCommand rawCmd() {
        return new SendNotificationCommand(
                "idem-1",
                USER.value(),
                NotificationKind.MARKETING,
                "할인",
                "여름 세일 30%",
                Map.of(),
                null);
    }

    private static SendNotificationCommand rawCmdSecurity() {
        return new SendNotificationCommand(
                "idem-2",
                USER.value(),
                NotificationKind.SECURITY,
                "OTP",
                "코드: 111",
                Map.of(),
                null);
    }

    private static Recipient recipient() {
        return new Recipient(
                USER,
                List.of(
                        new Channel(ChannelType.PUSH, "p".repeat(160)),
                        new Channel(ChannelType.EMAIL, "u@example.com"),
                        new Channel(ChannelType.SMS, "+821012345678")),
                Locale.KO_KR,
                KST);
    }
}
