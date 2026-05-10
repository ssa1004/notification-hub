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
 * <p>응답 형식은 FCM HTTP v1 규격을 흉내내 {@code projects/{project}/messages/{id}} 형태로
 * 반환 — 실제 SDK 로 교체 시 호출 측 (audit / 로깅 / 콜백 매칭) 코드가 그대로 동작하도록.
 *
 * <p>{@code vendor.fcm.failure-rate} (0.0~1.0) 비율로 무작위 실패. 실패는 4가지 케이스 중 하나:
 *
 * <ul>
 *   <li>{@link VendorInvalidRecipientException} — NOT_REGISTERED (단말 토큰 unregister).
 *       retry 무의미 + token 비활성화 대상.
 *   <li>{@link VendorPermanentException} — INVALID_ARGUMENT (페이로드 형식 오류). retry 무의미
 *       하지만 token 자체는 멀쩡 — 비활성화 X.
 *   <li>{@link VendorTransientException} — UNAVAILABLE (5xx). Resilience4j retry 대상.
 *   <li>{@link UncheckedIOException} — connection reset. Resilience4j retry 대상.
 * </ul>
 */
@Slf4j
@Component
public class MockFcmClient implements DeliveryGateway {

    /** FCM HTTP v1 의 message name 포맷. {@code projects/{project}/messages/{id}}. */
    private static final String MSG_ID_FORMAT = "projects/notification-hub/messages/%s";

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
            int kind = ThreadLocalRandom.current().nextInt(4);
            switch (kind) {
                case 0 -> {
                    log.warn(
                            "[MockFcmClient] 수신자 무효 (NOT_REGISTERED) attemptId={}",
                            attempt.id());
                    throw new VendorInvalidRecipientException(
                            "FCM messaging/registration-token-not-registered");
                }
                case 1 -> {
                    log.warn(
                            "[MockFcmClient] 영구 오류 (INVALID_ARGUMENT) attemptId={}",
                            attempt.id());
                    throw new VendorPermanentException(
                            "FCM messaging/invalid-argument");
                }
                case 2 -> {
                    log.warn("[MockFcmClient] 일시 오류 (UNAVAILABLE) attemptId={}", attempt.id());
                    throw new VendorTransientException("FCM messaging/server-unavailable");
                }
                default -> {
                    log.warn("[MockFcmClient] 네트워크 오류 attemptId={}", attempt.id());
                    throw new UncheckedIOException(new IOException("FCM connection reset"));
                }
            }
        }
        String msgId = String.format(MSG_ID_FORMAT, UUID.randomUUID());
        log.info(
                "[MockFcmClient] dispatched attemptId={} address={} msgId={}",
                attempt.id(),
                attempt.channel(),
                msgId);
        return msgId;
    }
}
