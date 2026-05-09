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
    @Retry(name = "vendorSes")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new VendorTransientException("SES transient");
        }
        String msgId = "ses-" + UUID.randomUUID();
        log.info("[MockSesClient] dispatched attemptId={} msgId={}", attempt.id(), msgId);
        return msgId;
    }
}
