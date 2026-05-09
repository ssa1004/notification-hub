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

/** AWS SES Mock. */
@Slf4j
@Component
public class MockSesClient implements DeliveryGateway {

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
                    throw new VendorPermanentException("SES MessageRejected");
                }
                case 1 -> {
                    log.warn("[MockSesClient] 일시 오류 (Throttling) attemptId={}", attempt.id());
                    throw new VendorTransientException("SES Throttling");
                }
                default -> {
                    log.warn("[MockSesClient] 네트워크 오류 attemptId={}", attempt.id());
                    throw new UncheckedIOException(new IOException("SES connection reset"));
                }
            }
        }
        String msgId = "ses-" + UUID.randomUUID();
        log.info("[MockSesClient] dispatched attemptId={} msgId={}", attempt.id(), msgId);
        return msgId;
    }
}
