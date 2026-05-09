package com.example.notification.application.service;

import com.example.notification.application.port.in.DispatchDeliveryUseCase;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeliveryGateway;
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
    private final Map<com.example.notification.domain.channel.ChannelType, DeliveryGateway>
            gatewaysByType = new EnumMap<>(com.example.notification.domain.channel.ChannelType.class);

    public DispatchDeliveryService(
            DeliveryAttemptRepository repository, List<DeliveryGateway> gateways) {
        this.repository = repository;
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
            // 도메인이 retry/EXHAUSTED 자동 처리. transient/permanent 구분은 메시지 prefix 로만.
            String prefix =
                    ex.getClass().getSimpleName().contains("Permanent")
                            ? FAIL_PREFIX_PERMANENT
                            : FAIL_PREFIX_TRANSIENT;
            log.warn("vendor failure id={} reason={}", attempt.id(), ex.getMessage());
            attempt.markFailed(prefix + ex.getMessage());
        }
        repository.save(attempt);
    }
}
