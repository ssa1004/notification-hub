package com.example.notification.application.service;

import com.example.notification.application.port.in.DispatchDeliveryUseCase;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.application.port.out.DeviceTokenRepository;
import com.example.notification.application.port.out.InvalidRecipientFailure;
import com.example.notification.application.port.out.PermanentDeliveryFailure;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING DeliveryAttempt 를 vendor 로 발송. 이 layer 가 channel → gateway 라우팅도 책임.
 *
 * <p>모든 {@link DeliveryGateway} 구현체가 Spring 으로 주입되고 channelType 으로 indexing.
 * 새 vendor 추가 = adapter 모듈에 새 {@code @Component} 만 등록.
 */
@Slf4j
@Service
public class DispatchDeliveryService implements DispatchDeliveryUseCase {

    static final String FAIL_PREFIX_PERMANENT = "permanent: ";
    static final String FAIL_PREFIX_TRANSIENT = "transient: ";

    private final DeliveryAttemptRepository repository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final Map<com.example.notification.domain.channel.ChannelType, DeliveryGateway>
            gatewaysByType = new EnumMap<>(com.example.notification.domain.channel.ChannelType.class);

    public DispatchDeliveryService(
            DeliveryAttemptRepository repository,
            DeviceTokenRepository deviceTokenRepository,
            List<DeliveryGateway> gateways) {
        this.repository = repository;
        this.deviceTokenRepository = deviceTokenRepository;
        for (DeliveryGateway g : gateways) {
            if (gatewaysByType.containsKey(g.channelType())) {
                throw new IllegalStateException(
                        "duplicate DeliveryGateway for channel: " + g.channelType());
            }
            gatewaysByType.put(g.channelType(), g);
        }
    }

    @Override
    @Transactional
    public void dispatch(UUID attemptId) {
        DeliveryAttempt attempt = repository
                .findById(attemptId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "deliveryAttempt not found: " + attemptId));
        if (attempt.isFinal()) {
            log.info("attempt already final id={} status={}", attempt.id(), attempt.status());
            return;
        }
        if (attempt.status() != DeliveryStatus.PENDING) {
            log.warn(
                    "attempt not in PENDING id={} status={}; skipping dispatch",
                    attempt.id(),
                    attempt.status());
            return;
        }
        attempt.markDispatching();
        DeliveryGateway gateway = gatewaysByType.get(attempt.channel().type());
        if (gateway == null) {
            attempt.markFailed("no gateway for channel: " + attempt.channel().type());
            repository.save(attempt);
            return;
        }
        try {
            String vendorMessageId = gateway.dispatch(attempt);
            attempt.markSucceeded(vendorMessageId);
        } catch (RuntimeException ex) {
            // 도메인이 retry/EXHAUSTED 자동 처리. transient/permanent 구분은 마커 인터페이스로.
            // (이전엔 클래스 simple name 의 "Permanent" 문자열 매칭이었으나 rename 한 줄로
            // 망가지고 IDE refactor 도 못 잡는 구조라 PermanentDeliveryFailure 마커로 교체.)
            boolean permanent = ex instanceof PermanentDeliveryFailure;
            String prefix = permanent ? FAIL_PREFIX_PERMANENT : FAIL_PREFIX_TRANSIENT;
            log.warn("vendor failure id={} reason={}", attempt.id(), ex.getMessage());
            attempt.markFailed(prefix + ex.getMessage());

            // PUSH 채널 + 수신자 식별자 자체 무효 (NOT_REGISTERED 등) → device token 비활성화.
            // 같은 영구 실패라도 payload 형식 오류 (FCM INVALID_ARGUMENT 등) 는 토큰 자체는
            // 멀쩡하므로 비활성화하면 안 된다 — 좁은 마커 InvalidRecipientFailure 로 분기.
            boolean recipientInvalid = ex instanceof InvalidRecipientFailure;
            if (recipientInvalid && attempt.channel().type() == ChannelType.PUSH) {
                try {
                    deviceTokenRepository.deactivateByToken(attempt.channel().address());
                    log.info(
                            "device token 비활성화 (수신자 식별자 무효) attemptId={} reason={}",
                            attempt.id(),
                            ex.getMessage());
                } catch (RuntimeException dx) {
                    // 비활성화 실패는 dispatch 결과에 영향 안 줌 — 다음 호출에서 다시 시도.
                    log.warn("device token 비활성화 실패: {}", dx.getMessage());
                }
            }
        }
        repository.save(attempt);
    }
}
