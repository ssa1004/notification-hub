package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FCM 호출의 Mock. 실제 SDK 의존성을 추가하지 않고 학습용으로 동작 시뮬레이션.
 *
 * <p>{@code vendor.fcm.failure-rate} (0.0~1.0) 비율로 무작위 실패. Resilience4j retry 가 적용되어
 * 실패 시 자동 재호출 — markFailed 까지는 가지 않을 수도 있음.
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
    @Retry(name = "vendorFcm")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.warn("[MockFcmClient] simulated failure attemptId={}", attempt.id());
            throw new VendorTransientException("FCM transient");
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
