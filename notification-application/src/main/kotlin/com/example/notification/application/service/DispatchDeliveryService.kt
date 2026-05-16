package com.example.notification.application.service

import com.example.notification.application.port.`in`.DispatchDeliveryUseCase
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.application.port.out.DeliveryGateway
import com.example.notification.application.port.out.DeviceTokenRepository
import com.example.notification.application.port.out.InvalidRecipientFailure
import com.example.notification.application.port.out.PermanentDeliveryFailure
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryStatus
import java.util.EnumMap
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * PENDING DeliveryAttempt 를 vendor 로 발송. 이 layer 가 channel → gateway 라우팅도 책임.
 *
 * 모든 [DeliveryGateway] 구현체가 Spring 으로 주입되고 channelType 으로 indexing.
 * 새 vendor 추가 = adapter 모듈에 새 `@Component` 만 등록.
 */
@Service
class DispatchDeliveryService(
    private val repository: DeliveryAttemptRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    gateways: List<DeliveryGateway>,
) : DispatchDeliveryUseCase {

    private val gatewaysByType: MutableMap<ChannelType, DeliveryGateway> =
        EnumMap(ChannelType::class.java)

    init {
        for (g in gateways) {
            check(!gatewaysByType.containsKey(g.channelType())) {
                "duplicate DeliveryGateway for channel: ${g.channelType()}"
            }
            gatewaysByType[g.channelType()] = g
        }
    }

    @Transactional
    override fun dispatch(deliveryAttemptId: UUID) {
        val attempt = repository
            .findById(deliveryAttemptId)
            .orElseThrow {
                IllegalArgumentException("deliveryAttempt not found: $deliveryAttemptId")
            }
        if (attempt.isFinal()) {
            log.info("attempt already final id={} status={}", attempt.id, attempt.status)
            return
        }
        if (attempt.status != DeliveryStatus.PENDING) {
            log.warn(
                "attempt not in PENDING id={} status={}; skipping dispatch",
                attempt.id,
                attempt.status,
            )
            return
        }
        attempt.markDispatching()
        val gateway = gatewaysByType[attempt.channel.type]
        if (gateway == null) {
            attempt.markFailed("no gateway for channel: ${attempt.channel.type}")
            repository.save(attempt)
            return
        }
        try {
            val vendorMessageId = gateway.dispatch(attempt)
            attempt.markSucceeded(vendorMessageId)
        } catch (ex: RuntimeException) {
            // 도메인이 retry/EXHAUSTED 자동 처리. transient/permanent 구분은 마커 인터페이스로.
            // (이전엔 클래스 simple name 의 "Permanent" 문자열 매칭이었으나 rename 한 줄로
            // 망가지고 IDE refactor 도 못 잡는 구조라 PermanentDeliveryFailure 마커로 교체.)
            val permanent = ex is PermanentDeliveryFailure
            val prefix = if (permanent) FAIL_PREFIX_PERMANENT else FAIL_PREFIX_TRANSIENT
            log.warn("vendor failure id={} reason={}", attempt.id, ex.message)
            attempt.markFailed(prefix + ex.message)

            // PUSH 채널 + 수신자 식별자 자체 무효 (NOT_REGISTERED 등) → device token 비활성화.
            // 같은 영구 실패라도 payload 형식 오류 (FCM INVALID_ARGUMENT 등) 는 토큰 자체는
            // 멀쩡하므로 비활성화하면 안 된다 — 좁은 마커 InvalidRecipientFailure 로 분기.
            val recipientInvalid = ex is InvalidRecipientFailure
            if (recipientInvalid && attempt.channel.type == ChannelType.PUSH) {
                try {
                    deviceTokenRepository.deactivateByToken(attempt.channel.address)
                    log.info(
                        "device token 비활성화 (수신자 식별자 무효) attemptId={} reason={}",
                        attempt.id,
                        ex.message,
                    )
                } catch (dx: RuntimeException) {
                    // 비활성화 실패는 dispatch 결과에 영향 안 줌 — 다음 호출에서 다시 시도.
                    log.warn("device token 비활성화 실패: {}", dx.message)
                }
            }
        }
        repository.save(attempt)
    }

    companion object {
        const val FAIL_PREFIX_PERMANENT: String = "permanent: "
        const val FAIL_PREFIX_TRANSIENT: String = "transient: "

        private val log = LoggerFactory.getLogger(DispatchDeliveryService::class.java)
    }
}
