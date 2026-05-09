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

/** Twilio SMS Mock. SMS body 90B 초과 시 vendor 가 분할 청구 — 여기선 길이만 로그로 안내. */
@Slf4j
@Component
public class MockTwilioClient implements DeliveryGateway {

    @Value("${vendor.twilio.failure-rate:0.0}")
    private double failureRate;

    @Override
    public ChannelType channelType() {
        return ChannelType.SMS;
    }

    @Override
    @Retry(name = "vendorTwilio")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new VendorTransientException("Twilio transient");
        }
        if (attempt.renderedBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 90) {
            log.info(
                    "[MockTwilioClient] LMS billing applied (body > 90B) attemptId={}",
                    attempt.id());
        }
        String msgId = "twilio-" + UUID.randomUUID();
        log.info("[MockTwilioClient] dispatched attemptId={} msgId={}", attempt.id(), msgId);
        return msgId;
    }
}
