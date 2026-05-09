package com.example.notification.application.service;

import com.example.notification.application.dto.SendNotificationCommand;
import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.exception.RecipientNotFoundException;
import com.example.notification.application.exception.TemplateNotFoundException;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeviceTokenRepository;
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
import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.example.notification.domain.notification.Notification;
import com.example.notification.domain.notification.NotificationFannedOut;
import com.example.notification.domain.notification.NotificationStatus;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.Recipient;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.IdempotencyKey;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.shared.RateLimitDecision;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 알림 발송. 흐름은 {@link SendNotificationUseCase} javadoc 참조.
 *
 * <p>Idempotency-Key 점유는 트랜잭션 *밖* 에서 가장 먼저 (DB 무관하게 차단). 이후 단계는 한
 * 트랜잭션 안에서 — DB write + Outbox write 가 atomic.
 *
 * <p>Rate limit 차단은 idempotency 점유 *후* — 같은 idempotencyKey 로 retry 한 호출은 rate
 * limit 까지 다시 소진하지 않도록 (이미 점유 실패가 먼저 떠야 함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendNotificationService implements SendNotificationUseCase {

    public static final Duration IDEM_TTL = Duration.ofHours(24);
    public static final String FANNED_OUT_TOPIC = "notification.fanned-out";
    public static final String DELIVERY_TOPIC_PREFIX = "notification.delivery.";

    private final IdempotencyStore idempotencyStore;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RecipientRepository recipientRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final TemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;
    private final RateLimiter rateLimiter;
    private final OutboxPublisher outboxPublisher;
    private final ChannelResolver channelResolver;
    private final AuditLogger auditLogger;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    @Transactional
    public SendNotificationResult send(SendNotificationCommand command) {
        IdempotencyKey idemKey = new IdempotencyKey(command.idempotencyKey());
        if (!idempotencyStore.tryAcquire(idemKey, IDEM_TTL)) {
            throw new DuplicateRequestException(idemKey.value());
        }

        RecipientId recipientId = new RecipientId(command.recipientId());
        Recipient recipient = recipientRepository
                .findById(recipientId)
                .orElseThrow(() -> new RecipientNotFoundException(recipientId.value()));

        UserPreference preference = userPreferenceRepository
                .findByRecipientId(recipientId)
                .orElseGet(() -> UserPreference.defaults(recipientId));

        Notification notification = Notification.accept(
                idemKey,
                recipientId,
                command.kind(),
                command.title() != null && !command.title().isBlank() ? command.title() : "(template)",
                command.body() != null && !command.body().isBlank() ? command.body() : "(template)",
                command.payload(),
                command.templateKey());

        Instant now = Instant.now();
        List<Channel> resolved = channelResolver.resolve(recipient, preference, command.kind(), now);
        List<Channel> channels = expandPushFanOut(recipientId, resolved);

        if (channels.isEmpty()) {
            notification.markSuppressed();
            notificationRepository.save(notification);
            String reason = channelResolver.suppressionReason(recipient, preference, command.kind(), now);
            auditLogger.log(
                    recipientId.value(),
                    "NOTIFICATION_SUPPRESSED",
                    Map.of(
                            "notificationId", notification.id().toString(),
                            "kind", command.kind().name(),
                            "reason", reason));
            log.info("notification suppressed id={} reason={}", notification.id(), reason);
            return new SendNotificationResult(
                    notification.id(),
                    NotificationStatus.SUPPRESSED,
                    List.of(),
                    reason);
        }

        // Rate limit — 하나라도 차단되면 묶음 자체 거절. 부분 발송은 사용자 혼란 유발.
        for (Channel ch : channels) {
            RateLimitDecision decision = rateLimiter.tryConsume(recipientId, ch.type());
            if (!decision.allowed()) {
                throw new RateLimitExceededException(ch.type().name(), decision.retryAfterMillis());
            }
        }

        Notification saved = notificationRepository.save(notification);
        List<DeliveryAttempt> attempts = createAttempts(saved, recipient.locale(), channels);
        List<DeliveryAttempt> persisted = deliveryAttemptRepository.saveAll(attempts);

        saved.markFannedOut();
        notificationRepository.save(saved);

        publishOutbox(saved, persisted);

        auditLogger.log(
                recipientId.value(),
                "NOTIFICATION_FANNED_OUT",
                Map.of(
                        "notificationId", saved.id().toString(),
                        "kind", command.kind().name(),
                        "channels", channels.stream().map(c -> c.type().name()).toList()));

        return new SendNotificationResult(
                saved.id(),
                NotificationStatus.FANNED_OUT,
                channels.stream().map(Channel::type).toList(),
                null);
    }

    /**
     * PUSH 채널을 사용자의 모든 active device token 으로 fan-out. ChannelResolver 가 반환한
     * PUSH 가 1개 (recipient.channels 에 들어있는 placeholder) 인 경우, 실제 active device 가
     * N개면 N개의 PUSH Channel 로 치환 — 각 device 가 별도 attempt 로 발송.
     *
     * <p>active device 가 0개면 PUSH 자체를 결과에서 제거 (사용자가 push 끄고 다른 채널만 받는
     * 케이스). PUSH 가 아닌 다른 채널 (EMAIL/SMS/KAKAO) 은 그대로 통과.
     */
    private List<Channel> expandPushFanOut(RecipientId recipientId, List<Channel> resolved) {
        boolean hasPush = resolved.stream().anyMatch(c -> c.type() == ChannelType.PUSH);
        if (!hasPush) {
            return resolved;
        }
        List<DeviceToken> activeDevices = deviceTokenRepository.findActiveByRecipientId(recipientId);
        List<Channel> expanded = new ArrayList<>(resolved.size() + activeDevices.size());
        for (Channel ch : resolved) {
            if (ch.type() != ChannelType.PUSH) {
                expanded.add(ch);
                continue;
            }
            // recipient 의 PUSH placeholder 는 무시하고 active device token 으로 N개 치환.
            // active 0 이면 push 발송 자체 안 함 (사용자가 device 등록 안 한 상태).
            for (DeviceToken d : activeDevices) {
                expanded.add(new Channel(ChannelType.PUSH, d.token()));
            }
        }
        return expanded;
    }

    private List<DeliveryAttempt> createAttempts(
            Notification notification, Locale locale, List<Channel> channels) {
        List<DeliveryAttempt> attempts = new ArrayList<>();
        for (Channel ch : channels) {
            String renderedTitle;
            String renderedBody;
            if (notification.templateKey() != null) {
                TemplateKey tKey = new TemplateKey(notification.templateKey());
                Template tpl = templateRepository
                        .findWithFallback(tKey, locale, ch.type())
                        .orElseThrow(
                                () -> new TemplateNotFoundException(
                                        tKey.value(), ch.type().name()));
                tpl.verifyPayloadCovers(notification.payload());
                TemplateRenderer.Rendered r =
                        templateRenderer.render(tpl, notification.payload());
                renderedTitle = r.title();
                renderedBody = r.body();
            } else {
                renderedTitle = notification.title();
                renderedBody = notification.body();
            }
            attempts.add(
                    DeliveryAttempt.create(
                            notification.id(), ch, renderedTitle, renderedBody));
        }
        return attempts;
    }

    private void publishOutbox(Notification notification, List<DeliveryAttempt> attempts) {
        outboxPublisher.publish(
                FANNED_OUT_TOPIC,
                notification.id().toString(),
                NotificationFannedOut.of(
                        notification.id(),
                        attempts.stream().map(DeliveryAttempt::id).toList()));

        for (DeliveryAttempt a : attempts) {
            ChannelType type = a.channel().type();
            outboxPublisher.publish(
                    DELIVERY_TOPIC_PREFIX + type.name().toLowerCase(),
                    a.id().toString(),
                    DeliveryRequested.of(notification.id(), a.id(), type));
        }
    }
}
