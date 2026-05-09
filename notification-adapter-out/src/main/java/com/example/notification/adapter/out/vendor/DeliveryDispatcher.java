package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer 가 호출하는 *실 발송* 컴포넌트.
 *
 * <ol>
 *   <li>PENDING attempt 조회 — 이미 final 이면 (콜백이 먼저 도착) 무시
 *   <li>markDispatching → vendor 호출 → 성공/실패 마킹
 *   <li>markFailed 의 도메인 로직이 retryCount 와 EXHAUSTED 까지 처리
 * </ol>
 *
 * <p>vendor 호출이 transient 실패면 다음 polling/consumer redelivery 에서 재시도. permanent
 * 실패면 retry 의미 없으므로 즉시 EXHAUSTED 까지 가도록 도메인이 카운트 누적.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryDispatcher {

    private final DeliveryAttemptRepository repository;
    private final DeliveryGatewayRouter router;

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
        try {
            DeliveryGateway gateway = router.gatewayFor(attempt.channel().type());
            String vendorMessageId = gateway.dispatch(attempt);
            attempt.markSucceeded(vendorMessageId);
        } catch (VendorPermanentException ex) {
            log.warn("permanent vendor failure id={} reason={}", attempt.id(), ex.getMessage());
            attempt.markFailed("permanent: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("transient vendor failure id={} reason={}", attempt.id(), ex.getMessage());
            attempt.markFailed("transient: " + ex.getMessage());
        }
        repository.save(attempt);
    }
}
