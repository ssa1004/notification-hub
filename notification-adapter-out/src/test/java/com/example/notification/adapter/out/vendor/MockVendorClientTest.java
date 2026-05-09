package com.example.notification.adapter.out.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Mock vendor client 의 *직접 호출* 테스트 (Resilience4j retry 없이). retry 통합 테스트는
 * Spring context 가 필요해서 별도.
 *
 * <p>여기선 mock 이 vendor 별 적절한 예외 종류 (5xx / 4xx / network) 를 던지는지 검증.
 */
class MockVendorClientTest {

    @Test
    void 실패율_0_이면_항상_성공_반환() {
        MockFcmClient sut = new MockFcmClient();
        setFailureRate(sut, 0.0);

        String msgId = sut.dispatch(pushAttempt());

        assertThat(msgId).startsWith("fcm-");
    }

    @RepeatedTest(50)
    void 실패율_1_이면_항상_3종_예외_중_하나_던짐() {
        MockFcmClient sut = new MockFcmClient();
        setFailureRate(sut, 1.0);

        // 50회 반복으로 3종 (Permanent / Transient / IOException) 중 어느 하나는 매번 던져짐.
        assertThatThrownBy(() -> sut.dispatch(pushAttempt()))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(VendorPermanentException.class),
                        ex -> assertThat(ex).isInstanceOf(VendorTransientException.class),
                        ex -> assertThat(ex).isInstanceOf(UncheckedIOException.class));
    }

    @Test
    void SES_도_동일_패턴() {
        MockSesClient sut = new MockSesClient();
        setFailureRate(sut, 0.0);
        assertThat(sut.dispatch(emailAttempt())).startsWith("ses-");
        assertThat(sut.channelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void Twilio_도_동일_패턴() {
        MockTwilioClient sut = new MockTwilioClient();
        setFailureRate(sut, 0.0);
        assertThat(sut.dispatch(smsAttempt())).startsWith("twilio-");
        assertThat(sut.channelType()).isEqualTo(ChannelType.SMS);
    }

    @Test
    void Kakao_도_동일_패턴() {
        MockKakaoAlimTalkClient sut = new MockKakaoAlimTalkClient();
        setFailureRate(sut, 0.0);
        assertThat(sut.dispatch(kakaoAttempt())).startsWith("kakao-");
        assertThat(sut.channelType()).isEqualTo(ChannelType.KAKAO_ALIMTALK);
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
}
