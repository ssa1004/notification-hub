package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 카카오 알림톡 Mock.
 *
 * <p>응답 형식은 카카오 비즈메시지 plus friend 의 message id 를 흉내내 {@code KKO-{uuid}} 형태.
 *
 * <p>vendor 정책상 야간 (KST 21:00~08:00) 발송은 정보성 알림톡 차단 — 차단되면
 * {@link VendorPermanentException} 으로 즉시 실패. ChannelResolver 단에서 야간 KAKAO_ALIMTALK
 * 을 채널 후보에서 제외하므로 1차 차단되지만 (선호도 / DND), 운영자가 강제 발송하거나 정책
 * 우회로 도달하더라도 vendor 단에서 final 거절. 광고성 알림톡과 무관한 SECURITY 알림이라도
 * vendor 정책은 동일.
 *
 * <p>실패 케이스:
 *
 * <ul>
 *   <li>{@link VendorPermanentException} — TEMPLATE_NOT_FOUND (등록 안 된 템플릿). retry 무의미.
 *   <li>{@link VendorPermanentException} — NIGHT_TIME_BLOCKED (야간 정책). retry 무의미.
 *   <li>{@link VendorTransientException} — 5xx. retry 대상.
 *   <li>{@link UncheckedIOException} — connection reset. retry 대상.
 * </ul>
 */
@Slf4j
@Component
public class MockKakaoAlimTalkClient implements DeliveryGateway {

    /** 카카오 알림톡 message id prefix. */
    private static final String MSG_ID_PREFIX = "KKO-";
    /** vendor 정책 — KST 야간 차단 시작 시각 (포함). */
    private static final LocalTime NIGHT_START = LocalTime.of(21, 0);
    /** vendor 정책 — KST 야간 차단 종료 시각 (제외). */
    private static final LocalTime NIGHT_END = LocalTime.of(8, 0);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${vendor.kakao.failure-rate:0.0}")
    private double failureRate;

    @Value("${vendor.kakao.enforce-night-block:false}")
    private boolean enforceNightBlock;

    private final Clock clock;

    public MockKakaoAlimTalkClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.KAKAO_ALIMTALK;
    }

    @Override
    @Retry(name = "kakao")
    public String dispatch(DeliveryAttempt attempt) {
        if (enforceNightBlock && isNightInKst()) {
            log.warn(
                    "[MockKakaoAlimTalkClient] vendor 정책 차단 (NIGHT_TIME_BLOCKED) attemptId={}",
                    attempt.id());
            throw new VendorPermanentException("Kakao NIGHT_TIME_BLOCKED (KST 21:00~08:00)");
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            int kind = ThreadLocalRandom.current().nextInt(3);
            switch (kind) {
                case 0 -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 영구 오류 (TEMPLATE_NOT_FOUND) attemptId={}",
                            attempt.id());
                    throw new VendorPermanentException("Kakao 5004: TEMPLATE_NOT_FOUND");
                }
                case 1 -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 일시 오류 (5xx) attemptId={}",
                            attempt.id());
                    throw new VendorTransientException("Kakao 5xx: Internal Server Error");
                }
                default -> {
                    log.warn(
                            "[MockKakaoAlimTalkClient] 네트워크 오류 attemptId={}",
                            attempt.id());
                    throw new UncheckedIOException(new IOException("Kakao connection reset"));
                }
            }
        }
        String msgId = MSG_ID_PREFIX + UUID.randomUUID();
        log.info(
                "[MockKakaoAlimTalkClient] dispatched attemptId={} msgId={}",
                attempt.id(),
                msgId);
        return msgId;
    }

    private boolean isNightInKst() {
        LocalTime now = LocalTime.now(clock.withZone(KST));
        // wraps midnight: 21:00 ≤ now OR now < 08:00
        return !now.isBefore(NIGHT_START) || now.isBefore(NIGHT_END);
    }
}
