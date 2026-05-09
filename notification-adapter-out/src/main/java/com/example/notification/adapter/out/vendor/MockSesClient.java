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
 * AWS SES Mock.
 *
 * <p>응답 형식은 SES SendEmail 의 MessageId — RFC 5322 Message-ID 와 비슷한 형태로
 * {@code <{uuid}@email.amazonses.com>} 반환. 실제 SDK 로 교체 시 audit / 콜백 매칭 코드가
 * 그대로 동작.
 *
 * <p>실패 케이스:
 *
 * <ul>
 *   <li>{@link VendorPermanentException} — MessageRejected (sandbox / 미인증 도메인). retry 무의미.
 *   <li>{@link VendorTransientException} — Throttling (sending quota 초과). retry 대상.
 *   <li>{@link UncheckedIOException} — connection reset. retry 대상.
 * </ul>
 */
@Slf4j
@Component
public class MockSesClient implements DeliveryGateway {

    /** SES MessageId 포맷 — {@code <{uuid}@email.amazonses.com>}. */
    private static final String MSG_ID_FORMAT = "<%s@email.amazonses.com>";

    @Value("${vendor.ses.failure-rate:0.0}")
    private double failureRate;

    @Override
    public ChannelType channelType() {
        return ChannelType.EMAIL;
    }

    @Override
    @Retry(name = "ses")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            int kind = ThreadLocalRandom.current().nextInt(3);
            switch (kind) {
                case 0 -> {
                    log.warn(
                            "[MockSesClient] 영구 오류 (MessageRejected) attemptId={}",
                            attempt.id());
                    throw new VendorPermanentException(
                            "SES MessageRejected: Email address not verified");
                }
                case 1 -> {
                    log.warn(
                            "[MockSesClient] 일시 오류 (Throttling) attemptId={}", attempt.id());
                    throw new VendorTransientException("SES Throttling: Maximum sending rate exceeded");
                }
                default -> {
                    log.warn("[MockSesClient] 네트워크 오류 attemptId={}", attempt.id());
                    throw new UncheckedIOException(new IOException("SES connection reset"));
                }
            }
        }
        String msgId = String.format(MSG_ID_FORMAT, UUID.randomUUID());
        log.info("[MockSesClient] dispatched attemptId={} msgId={}", attempt.id(), msgId);
        return msgId;
    }
}
