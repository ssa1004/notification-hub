package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.TemplateEntity;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;

public final class TemplateMapper {

    private TemplateMapper() {}

    public static TemplateEntity toEntity(Template t) {
        TemplateEntity e = new TemplateEntity();
        e.setId(t.id());
        e.setTemplateKey(t.key().value());
        e.setLocale(t.locale().tag());
        e.setChannelType(t.channelType());
        e.setTitleTemplate(t.titleTemplate());
        e.setBodyTemplate(t.bodyTemplate());
        e.setCreatedAt(t.createdAt());
        return e;
    }

    public static Template toDomain(TemplateEntity e) {
        return new Template(
                e.getId(),
                new TemplateKey(e.getTemplateKey()),
                new Locale(e.getLocale()),
                e.getChannelType(),
                e.getTitleTemplate(),
                e.getBodyTemplate(),
                e.getCreatedAt());
    }
}
