package com.example.notification.adapter.out.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Mock vendor client 의 *직접 호출* 테스트 (Resilience4j retry 없이). retry 통합 테스트는
 * Spring context 가 필요해서 별도.
 *
 * <p>여기선 mock 이 vendor 별 적절한 예외 종류 (5xx / 4xx / network) 를 던지는지, 그리고
 * 응답 message id 가 vendor 의 실제 형식 (FCM의 message name, SES의 RFC5322, Twilio SID,
 * 카카오 KKO prefix) 을 흉내내는지 검증.
 */
class MockVendorClientTest {

    @Test
    void 실패율_0_이면_항상_성공_반환() {
        MockFcmClient sut = new MockFcmClient();
        setFailureRate(sut, 0.0);

        String msgId = sut.dispatch(pushAttempt());

        // FCM HTTP v1 응답 포맷: projects/{project}/messages/{id}
        assertThat(msgId).matches("^projects/[^/]+/messages/[0-9a-f-]{36}$");
    }

    @RepeatedTest(50)
    void 실패율_1_이면_항상_지정_예외_중_하나_던짐() {
        MockFcmClient sut = new MockFcmClient();
        setFailureRate(sut, 1.0);

        // Permanent 2종 (NOT_REGISTERED / INVALID_ARGUMENT) + Transient + IOException = 4종 분기.
        assertThatThrownBy(() -> sut.dispatch(pushAttempt()))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(VendorPermanentException.class),
                        ex -> assertThat(ex).isInstanceOf(VendorTransientException.class),
                        ex -> assertThat(ex).isInstanceOf(UncheckedIOException.class));
    }

    @Test
    void SES_응답은_RFC_5322_Message_ID_형식() {
        MockSesClient sut = new MockSesClient();
        setFailureRate(sut, 0.0);
        String msgId = sut.dispatch(emailAttempt());
        // SES MessageId: <{uuid}@email.amazonses.com>
        assertThat(msgId).matches("^<[0-9a-f-]{36}@email\\.amazonses\\.com>$");
        assertThat(sut.channelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void Twilio_응답은_SM_prefix_34자_SID() {
        MockTwilioClient sut = new MockTwilioClient();
        setFailureRate(sut, 0.0);
        String msgId = sut.dispatch(smsAttempt());
        // Twilio Message SID: SM + 32자 hex
        assertThat(msgId).matches("^SM[0-9a-f]{32}$");
        assertThat(sut.channelType()).isEqualTo(ChannelType.SMS);
    }

    @Test
    void Kakao_응답은_KKO_prefix() {
        MockKakaoAlimTalkClient sut =
                new MockKakaoAlimTalkClient(Clock.fixed(midDay(), ZoneId.of("Asia/Seoul")));
        setFailureRate(sut, 0.0);
        String msgId = sut.dispatch(kakaoAttempt());
        assertThat(msgId).startsWith("KKO-");
        assertThat(sut.channelType()).isEqualTo(ChannelType.KAKAO_ALIMTALK);
    }

    @Test
    void Kakao_야간_정책_enforce_시_KST_22시는_영구_실패() {
        // 2025-01-15 22:00 KST = 13:00 UTC
        Clock kstNight = Clock.fixed(Instant.parse("2025-01-15T13:00:00Z"), ZoneId.of("UTC"));
        MockKakaoAlimTalkClient sut = new MockKakaoAlimTalkClient(kstNight);
        setFailureRate(sut, 0.0);
        setEnforceNightBlock(sut, true);

        assertThatThrownBy(() -> sut.dispatch(kakaoAttempt()))
                .isInstanceOf(VendorPermanentException.class)
                .hasMessageContaining("NIGHT_TIME_BLOCKED");
    }

    @Test
    void Kakao_야간_정책_enforce_off_면_야간이라도_통과() {
        Clock kstNight = Clock.fixed(Instant.parse("2025-01-15T13:00:00Z"), ZoneId.of("UTC"));
        MockKakaoAlimTalkClient sut = new MockKakaoAlimTalkClient(kstNight);
        setFailureRate(sut, 0.0);
        setEnforceNightBlock(sut, false);

        String msgId = sut.dispatch(kakaoAttempt());
        assertThat(msgId).startsWith("KKO-");
    }

    @Test
    void Kakao_야간_정책_enforce_시_낮시간은_통과() {
        // 2025-01-15 12:00 KST = 03:00 UTC
        Clock kstNoon = Clock.fixed(Instant.parse("2025-01-15T03:00:00Z"), ZoneId.of("UTC"));
        MockKakaoAlimTalkClient sut = new MockKakaoAlimTalkClient(kstNoon);
        setFailureRate(sut, 0.0);
        setEnforceNightBlock(sut, true);

        String msgId = sut.dispatch(kakaoAttempt());
        assertThat(msgId).startsWith("KKO-");
    }

    /** 2025-01-15 정오 KST. Kakao 야간 차단 (21~08 KST) 밖. */
    private static Instant midDay() {
        return Instant.parse("2025-01-15T03:00:00Z");
    }

    private static DeliveryAttempt pushAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.PUSH, "a".repeat(100)),
                "title",
                "body");
    }

    private static DeliveryAttempt emailAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.EMAIL, "user@example.com"),
                "title",
                "body");
    }

    private static DeliveryAttempt smsAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.SMS, "+821012345678"),
                "title",
                "body");
    }

    private static DeliveryAttempt kakaoAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.KAKAO_ALIMTALK, "+821012345678"),
                "title",
                "body");
    }

    private static void setFailureRate(Object client, double rate) {
        try {
            Field f = client.getClass().getDeclaredField("failureRate");
            f.setAccessible(true);
            f.setDouble(client, rate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setEnforceNightBlock(Object client, boolean enforce) {
        try {
            Field f = client.getClass().getDeclaredField("enforceNightBlock");
            f.setAccessible(true);
            f.setBoolean(client, enforce);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
