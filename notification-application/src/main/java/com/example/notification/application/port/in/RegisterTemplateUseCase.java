package com.example.notification.application.port.in;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.template.Template;

/** 운영자 (또는 신뢰된 시스템) 가 템플릿 등록. (key, locale, channelType) 조합이 unique. */
public interface RegisterTemplateUseCase {

    Template register(RegisterCommand command);

    record RegisterCommand(
            String key,
            Locale locale,
            ChannelType channelType,
            String titleTemplate,
            String bodyTemplate) {}
}
