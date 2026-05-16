package com.example.notification.application.service

import com.example.notification.application.dto.SendNotificationCommand
import com.example.notification.application.dto.SendNotificationResult
import com.example.notification.application.exception.DuplicateRequestException
import com.example.notification.application.exception.RateLimitExceededException
import com.example.notification.application.exception.RecipientNotFoundException
import com.example.notification.application.exception.TemplateNotFoundException
import com.example.notification.application.port.`in`.SendNotificationUseCase
import com.example.notification.application.port.out.AuditLogger
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.application.port.out.DeviceTokenRepository
import com.example.notification.application.port.out.IdempotencyStore
import com.example.notification.application.port.out.NotificationRepository
import com.example.notification.application.port.out.OutboxPublisher
import com.example.notification.application.port.out.RateLimiter
import com.example.notification.application.port.out.RecipientRepository
import com.example.notification.application.port.out.TemplateRenderer
import com.example.notification.application.port.out.TemplateRepository
import com.example.notification.application.port.out.UserPreferenceRepository
import com.example.notification.domain.channel.Channel
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryRequested
import com.example.notification.domain.notification.Notification
import com.example.notification.domain.notification.NotificationFannedOut
import com.example.notification.domain.notification.NotificationStatus
import com.example.notification.domain.preference.UserPreference
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.IdempotencyKey
import com.example.notification.domain.shared.Locale
import com.example.notification.domain.template.TemplateKey
import java.time.Duration
import java.time.Instant
import java.util.EnumMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한 알림 발송. 흐름은 [SendNotificationUseCase] javadoc 참조.
 *
 * Idempotency-Key 점유는 트랜잭션 밖에서 가장 먼저 (DB 무관하게 차단). 이후 단계는 한
 * 트랜잭션 안에서 — DB write + Outbox write 가 atomic.
 *
 * Rate limit 차단은 idempotency 점유 후 — 같은 idempotencyKey 로 retry 한 호출은 rate
 * limit 까지 다시 소진하지 않도록 (이미 점유 실패가 먼저 떠야 함).
 */
@Service
class SendNotificationService(
    private val idempotencyStore: IdempotencyStore,
    private val notificationRepository: NotificationRepository,
    private val deliveryAttemptRepository: DeliveryAttemptRepository,
    private val recipientRepository: RecipientRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val templateRepository: TemplateRepository,
    private val templateRenderer: TemplateRenderer,
    private val rateLimiter: RateLimiter,
    private val outboxPublisher: OutboxPublisher,
    private val channelResolver: ChannelResolver,
    private val auditLogger: AuditLogger,
    private val deviceTokenRepository: DeviceTokenRepository,
) : SendNotificationUseCase {

    @Transactional
    override fun send(command: SendNotificationCommand): SendNotificationResult {
        val idemKey = IdempotencyKey(command.idempotencyKey)
        if (!idempotencyStore.tryAcquire(idemKey, IDEM_TTL)) {
            throw DuplicateRequestException(idemKey.value)
        }

        val recipientId = RecipientId(command.recipientId)
        val recipient = recipientRepository
            .findById(recipientId)
            .orElseThrow { RecipientNotFoundException(recipientId.value) }

        val preference: UserPreference = userPreferenceRepository
            .findByRecipientId(recipientId)
            .orElseGet { UserPreference.defaults(recipientId) }

        val notification = Notification.accept(
            idemKey,
            recipientId,
            command.kind,
            command.title?.takeIf { it.isNotBlank() } ?: "(template)",
            command.body?.takeIf { it.isNotBlank() } ?: "(template)",
            command.payload,
            command.templateKey,
        )

        val now = Instant.now()
        val resolved = channelResolver.resolve(recipient, preference, command.kind, now)
        val channels = expandPushFanOut(recipientId, resolved)

        if (channels.isEmpty()) {
            notification.markSuppressed()
            notificationRepository.save(notification)
            val reason = channelResolver.suppressionReason(recipient, preference, command.kind, now)
            auditLogger.log(
                recipientId.value,
                "NOTIFICATION_SUPPRESSED",
                mapOf(
                    "notificationId" to notification.id.toString(),
                    "kind" to command.kind.name,
                    "reason" to reason,
                ),
            )
            log.info("notification suppressed id={} reason={}", notification.id, reason)
            return SendNotificationResult(
                notification.id,
                NotificationStatus.SUPPRESSED,
                emptyList(),
                reason,
            )
        }

        // Rate limit — 묶음 자체 거절. 채널별로 따로 tryConsume 하면 channel#1 통과 (토큰
        // 차감) 후 channel#2 거절 시 channel#1 토큰만 부분 소진되는 leak 발생. 원자 batch 로
        // "전부 가능하면 일괄 차감 / 하나라도 부족하면 모두 그대로 둠" 보장 (Redis Lua).
        val demand: MutableMap<ChannelType, Int> = EnumMap(ChannelType::class.java)
        for (ch in channels) {
            demand.merge(ch.type, 1) { a, b -> a + b }
        }
        val decisions = rateLimiter.tryConsumeAll(recipientId, demand)
        for ((channelType, decision) in decisions) {
            if (!decision.allowed) {
                throw RateLimitExceededException(channelType.name, decision.retryAfterMillis)
            }
        }

        val saved = notificationRepository.save(notification)
        val attempts = createAttempts(saved, recipient.locale, channels)
        val persisted = deliveryAttemptRepository.saveAll(attempts)

        saved.markFannedOut()
        notificationRepository.save(saved)

        publishOutbox(saved, persisted)

        auditLogger.log(
            recipientId.value,
            "NOTIFICATION_FANNED_OUT",
            mapOf(
                "notificationId" to saved.id.toString(),
                "kind" to command.kind.name,
                "channels" to channels.map { it.type.name },
            ),
        )

        return SendNotificationResult(
            saved.id,
            NotificationStatus.FANNED_OUT,
            channels.map { it.type },
            null,
        )
    }

    /**
     * PUSH 채널을 사용자의 모든 active device token 으로 fan-out. ChannelResolver 가 반환한
     * PUSH 가 1개 (recipient.channels 에 들어있는 placeholder) 인 경우, 실제 active device 가
     * N개면 N개의 PUSH Channel 로 치환 — 각 device 가 별도 attempt 로 발송.
     *
     * active device 가 0개면 PUSH 자체를 결과에서 제거 (사용자가 push 끄고 다른 채널만 받는
     * 케이스). PUSH 가 아닌 다른 채널 (EMAIL/SMS/KAKAO) 은 그대로 통과.
     */
    private fun expandPushFanOut(recipientId: RecipientId, resolved: List<Channel>): List<Channel> {
        val hasPush = resolved.any { it.type == ChannelType.PUSH }
        if (!hasPush) {
            return resolved
        }
        val activeDevices = deviceTokenRepository.findActiveByRecipientId(recipientId)
        val expanded = ArrayList<Channel>(resolved.size + activeDevices.size)
        for (ch in resolved) {
            if (ch.type != ChannelType.PUSH) {
                expanded.add(ch)
                continue
            }
            // recipient 의 PUSH placeholder 는 무시하고 active device token 으로 N개 치환.
            // active 0 이면 push 발송 자체 안 함 (사용자가 device 등록 안 한 상태).
            for (d in activeDevices) {
                expanded.add(Channel(ChannelType.PUSH, d.token))
            }
        }
        return expanded
    }

    private fun createAttempts(
        notification: Notification,
        locale: Locale,
        channels: List<Channel>,
    ): List<DeliveryAttempt> {
        val attempts = ArrayList<DeliveryAttempt>()
        for (ch in channels) {
            val renderedTitle: String
            val renderedBody: String
            val templateKey = notification.templateKey
            if (templateKey != null) {
                val tKey = TemplateKey(templateKey)
                val tpl = templateRepository
                    .findWithFallback(tKey, locale, ch.type)
                    .orElseThrow { TemplateNotFoundException(tKey.value, ch.type.name) }
                tpl.verifyPayloadCovers(notification.payload())
                val r = templateRenderer.render(tpl, notification.payload())
                renderedTitle = r.title
                renderedBody = r.body
            } else {
                renderedTitle = notification.title
                renderedBody = notification.body
            }
            attempts.add(DeliveryAttempt.create(notification.id, ch, renderedTitle, renderedBody))
        }
        return attempts
    }

    private fun publishOutbox(notification: Notification, attempts: List<DeliveryAttempt>) {
        outboxPublisher.publish(
            FANNED_OUT_TOPIC,
            notification.id.toString(),
            NotificationFannedOut.of(notification.id, attempts.map { it.id }),
        )

        for (a in attempts) {
            val type = a.channel.type
            outboxPublisher.publish(
                DELIVERY_TOPIC_PREFIX + type.name.lowercase(),
                a.id.toString(),
                DeliveryRequested.of(notification.id, a.id, type),
            )
        }
    }

    companion object {
        @JvmField
        val IDEM_TTL: Duration = Duration.ofHours(24)
        const val FANNED_OUT_TOPIC: String = "notification.fanned-out"
        const val DELIVERY_TOPIC_PREFIX: String = "notification.delivery."

        private val log = LoggerFactory.getLogger(SendNotificationService::class.java)
    }
}
