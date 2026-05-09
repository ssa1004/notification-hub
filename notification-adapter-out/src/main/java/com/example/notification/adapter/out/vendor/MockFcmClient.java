package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FCM 호출의 Mock. 실제 SDK 의존성을 추가하지 않고 학습용으로 동작 시뮬레이션.
 *
 * <p>{@code vendor.fcm.failure-rate} (0.0~1.0) 비율로 무작위 실패. 실패 중 1/3 은 4xx
 * (NOT_REGISTERED 같은 영구 오류 — retry 무의미), 1/3 은 5xx (transient — Resilience4j retry
 * 대상), 1/3 은 IOException (network — Resilience4j retry 대상). Resilience4j retry 가 적용되어
 * 5xx/IO 는 자동 재호출.
 */
@Slf4j
@Component
public class MockFcmClient implements DeliveryGateway {

    @Value("${vendor.fcm.failure-rate:0.0}")
    private double failureRate;

    @Override
    public ChannelType channelType() {
        return ChannelType.PUSH;
    }

    @Override
    @Retry(name = "fcm")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            int kind = ThreadLocalRandom.current().nextInt(3);
            switch (kind) {
                case 0 -> {
                    log.warn(
                            "[MockFcmClient] 영구 오류 (NOT_REGISTERED) attemptId={}", attempt.id());
                    throw new VendorPermanentException("FCM NOT_REGISTERED");
                }
                case 1 -> {
                    log.warn("[MockFcmClient] 일시 오류 (5xx) attemptId={}", attempt.id());
                    throw new VendorTransientException("FCM 5xx");
                }
                default -> {
                    log.warn("[MockFcmClient] 네트워크 오류 attemptId={}", attempt.id());
                    throw new UncheckedIOException(new IOException("FCM connection reset"));
                }
            }
        }
        String msgId = "fcm-" + UUID.randomUUID();
        log.info(
                "[MockFcmClient] dispatched attemptId={} address={} msgId={}",
                attempt.id(),
                attempt.channel(),
                msgId);
        return msgId;
    }
}
