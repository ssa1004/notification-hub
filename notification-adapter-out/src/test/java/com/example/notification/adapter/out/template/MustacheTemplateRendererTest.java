package com.example.notification.adapter.out.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification.application.port.out.TemplateRenderer.Rendered;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MustacheTemplateRendererTest {

    private final MustacheTemplateRenderer renderer = new MustacheTemplateRenderer();

    @Test
    void single_brace_placeholders_substituted() {
        Template t = Template.register(
                new TemplateKey("auth.otp.v1"),
                Locale.KO_KR,
                ChannelType.SMS,
                "OTP",
                "[Acme] OTP {code} ({validMin}분간 유효)");
        Rendered r = renderer.render(t, Map.of("code", "654321", "validMin", "5"));
        assertThat(r.body()).isEqualTo("[Acme] OTP 654321 (5분간 유효)");
        assertThat(r.title()).isEqualTo("OTP");
    }

    @Test
    void title_can_have_placeholder_too() {
        Template t = Template.register(
                new TemplateKey("greet.welcome.v1"),
                Locale.KO_KR,
                ChannelType.PUSH,
                "{name}님 환영합니다",
                "지금부터 알림을 받아보세요.");
        Rendered r = renderer.render(t, Map.of("name", "홍길동"));
        assertThat(r.title()).isEqualTo("홍길동님 환영합니다");
    }

    @Test
    void missing_variable_renders_empty_string() {
        Template t = Template.register(
                new TemplateKey("foo.bar.v1"),
                Locale.KO_KR,
                ChannelType.PUSH,
                "title",
                "Hello {name}");
        // Mustache 의 기본 동작: 변수 없으면 빈 문자열로. (verifyPayloadCovers 가 미리 거름)
        Rendered r = renderer.render(t, Map.of());
        assertThat(r.body()).isEqualTo("Hello ");
    }
}
