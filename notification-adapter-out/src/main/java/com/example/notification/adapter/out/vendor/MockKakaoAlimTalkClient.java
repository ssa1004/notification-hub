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

/** 카카오 알림톡 Mock. */
@Slf4j
@Component
public class MockKakaoAlimTalkClient implements DeliveryGateway {

    @Value("${vendor.kakao.failure-rate:0.0}")
    private double failureRate;

    @Override
    public ChannelType channelType() {
        return ChannelType.KAKAO_ALIMTALK;
    }

    @Override
    @Retry(name = "kakao")
    public String dispatch(DeliveryAttempt attempt) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            int kind = ThreadLocalRandom.current().nextInt(3);
            switch (kind) {
                case 0 -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 영구 오류 (TEMPLATE_NOT_FOUND) attemptId={}",
                            attempt.id());
                    throw new VendorPermanentException("Kakao TEMPLATE_NOT_FOUND");
                }
                case 1 -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 일시 오류 (5xx) attemptId={}",
                            attempt.id());
                    throw new VendorTransientException("Kakao AlimTalk 5xx");
                }
                default -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 네트워크 오류 attemptId={}",
                            attempt.id());
                    throw new UncheckedIOException(new IOException("Kakao connection reset"));
                }
            }
        }
        String msgId = "kakao-" + UUID.randomUUID();
        log.info("[MockKakaoAlimTalkClient] dispatched attemptId={} msgId={}", attempt.id(), msgId);
        return msgId;
    }
}
