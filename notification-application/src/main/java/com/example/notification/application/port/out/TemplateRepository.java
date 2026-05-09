package com.example.notification.application.port.out;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import java.util.Optional;

/** Template 조회/등록. (key, locale, channelType) 가 unique. */
public interface TemplateRepository {

    Template save(Template template);

    Optional<Template> find(TemplateKey key, Locale locale, ChannelType channelType);

    /**
     * locale 우선 조회 후 fallback. 예: en-us 가 없으면 ko-kr 로. 기본 fallback locale 은
     * `Locale.KO_KR`.
     */
    Optional<Template> findWithFallback(
            TemplateKey key, Locale preferred, ChannelType channelType);
}
