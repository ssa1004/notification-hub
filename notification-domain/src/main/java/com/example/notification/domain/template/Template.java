package com.example.notification.domain.template;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 다국어 알림 템플릿. (key, locale, channelType) 가 unique.
 *
 * <p>본문은 Mustache placeholder 를 포함합니다 — {@code 안녕하세요 {name} 님, 결제 {amount}원이
 * 처리되었습니다}. 실제 치환은 use case 에서 {@code TemplateRenderer} 포트가 담당.
 *
 * <p>채널별 본문이 다를 수 있습니다 — SMS 는 90B 짧은 텍스트, 이메일은 풀 HTML. 따라서 같은 key
 * + locale 안에서도 channelType 이 다르면 별도 row.
 */
public final class Template {

    private final UUID id;
    private final TemplateKey key;
    private final Locale locale;
    private final ChannelType channelType;
    private final String titleTemplate;
    private final String bodyTemplate;
    private final Instant createdAt;

    public Template(
            UUID id,
            TemplateKey key,
            Locale locale,
            ChannelType channelType,
            String titleTemplate,
            String bodyTemplate,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.key = Objects.requireNonNull(key);
        this.locale = Objects.requireNonNull(locale);
        this.channelType = Objects.requireNonNull(channelType);
        Objects.requireNonNull(titleTemplate, "titleTemplate must not be null");
        Objects.requireNonNull(bodyTemplate, "bodyTemplate must not be null");
        if (titleTemplate.isBlank() || titleTemplate.length() > 200) {
            throw new IllegalArgumentException("titleTemplate length 1..200 required");
        }
        if (bodyTemplate.isBlank() || bodyTemplate.length() > 4000) {
            throw new IllegalArgumentException("bodyTemplate length 1..4000 required");
        }
        validateChannelLimit(channelType, bodyTemplate);
        this.titleTemplate = titleTemplate;
        this.bodyTemplate = bodyTemplate;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Template register(
            TemplateKey key,
            Locale locale,
            ChannelType channelType,
            String titleTemplate,
            String bodyTemplate) {
        return new Template(
                UUID.randomUUID(),
                key,
                locale,
                channelType,
                titleTemplate,
                bodyTemplate,
                Instant.now());
    }

    /**
     * 치환 후 길이가 채널별 한도를 분명히 넘을 수 있는 경우만 사전 차단.
     *
     * <p>SMS 한국 LMS = 2000B 이고 SMS = 90B. placeholder 가 있으면 정확한 길이를 알 수 없으므로
     * 여기서는 템플릿 raw 길이로 sanity check 만. 실제 치환 후 길이 검증은 vendor 가 함.
     */
    private static void validateChannelLimit(ChannelType type, String body) {
        if (type == ChannelType.SMS && body.length() > 2000) {
            throw new IllegalArgumentException("SMS body must be <= 2000 chars (LMS limit)");
        }
    }

    /**
     * 변수 셋이 본문 placeholder 를 모두 충족하는지 사전 검증.
     *
     * <p>{@code {name}} 같은 single-tag placeholder 만 검사 (Mustache 의 sections 는 검사 X).
     * 누락 시 빈 문자열로 치환되는 vendor 도 있어 일찍 막는 편이 안전.
     */
    public void verifyPayloadCovers(Map<String, String> payload) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(bodyTemplate + titleTemplate);
        while (m.find()) {
            String var = m.group(1);
            if (!payload.containsKey(var)) {
                throw new IllegalArgumentException(
                        "template payload missing variable: " + var
                                + " (template=" + key + ")");
            }
        }
    }

    public UUID id() {
        return id;
    }

    public TemplateKey key() {
        return key;
    }

    public Locale locale() {
        return locale;
    }

    public ChannelType channelType() {
        return channelType;
    }

    public String titleTemplate() {
        return titleTemplate;
    }

    public String bodyTemplate() {
        return bodyTemplate;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
