package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.TemplateEntity
import com.example.notification.domain.shared.Locale
import com.example.notification.domain.template.Template
import com.example.notification.domain.template.TemplateKey

object TemplateMapper {

    @JvmStatic
    fun toEntity(t: Template): TemplateEntity = TemplateEntity().apply {
        id = t.id
        templateKey = t.key.value
        locale = t.locale.tag
        channelType = t.channelType
        titleTemplate = t.titleTemplate
        bodyTemplate = t.bodyTemplate
        createdAt = t.createdAt
    }

    @JvmStatic
    fun toDomain(e: TemplateEntity): Template =
        Template(
            e.id,
            TemplateKey(e.templateKey),
            Locale(e.locale),
            e.channelType,
            e.titleTemplate,
            e.bodyTemplate,
            e.createdAt,
        )
}
