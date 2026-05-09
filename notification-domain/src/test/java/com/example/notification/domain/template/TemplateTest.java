package com.example.notification.domain.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateTest {

    @Test
    void register_creates_unique_id_and_now_timestamp() {
        Template t = sample();
        assertThat(t.id()).isNotNull();
        assertThat(t.createdAt()).isNotNull();
    }

    @Test
    void key_validates_format() {
        assertThatThrownBy(() -> new TemplateKey("Bad-Key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateKey("nodot"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TemplateKey("order.shipped.v1").value())
                .isEqualTo("order.shipped.v1");
    }

    @Test
    void verify_payload_throws_when_variable_missing() {
        Template t = Template.register(
                new TemplateKey("auth.otp.v1"),
                Locale.KO_KR,
                ChannelType.SMS,
                "OTP",
                "[Acme] OTP {code} ({validMin}분간 유효)");
        assertThatThrownBy(() -> t.verifyPayloadCovers(Map.of("code", "123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validMin");
    }

    @Test
    void verify_payload_passes_when_complete() {
        Template t = sample();
        t.verifyPayloadCovers(Map.of("name", "홍길동"));
    }

    @Test
    void sms_body_over_2000_rejected() {
        String body = "안녕 {name} ".repeat(300);
        assertThatThrownBy(
                        () -> Template.register(
                                new TemplateKey("foo.bar.v1"),
                                Locale.KO_KR,
                                ChannelType.SMS,
                                "title",
                                body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LMS");
    }

    private Template sample() {
        return Template.register(
                new TemplateKey("order.shipped.v1"),
                Locale.KO_KR,
                ChannelType.PUSH,
                "발송 안내",
                "{name}님 주문이 출고되었습니다.");
    }
}
