package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.mapper.TemplateMapper
import com.example.notification.adapter.out.persistence.repository.TemplateJpaRepository
import com.example.notification.application.port.out.TemplateRepository
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.shared.Locale
import com.example.notification.domain.template.Template
import com.example.notification.domain.template.TemplateKey
import java.util.Optional
import org.springframework.stereotype.Repository

@Repository
class JpaTemplateRepository(
    private val jpa: TemplateJpaRepository,
) : TemplateRepository {

    override fun save(template: Template): Template =
        TemplateMapper.toDomain(jpa.save(TemplateMapper.toEntity(template)))

    override fun find(key: TemplateKey, locale: Locale, channelType: ChannelType): Optional<Template> =
        jpa.findByTemplateKeyAndLocaleAndChannelType(key.value, locale.tag, channelType)
            .map(TemplateMapper::toDomain)

    override fun findWithFallback(
        key: TemplateKey,
        preferred: Locale,
        channelType: ChannelType,
    ): Optional<Template> {
        val direct = find(key, preferred, channelType)
        if (direct.isPresent) return direct
        if (preferred != Locale.KO_KR) {
            return find(key, Locale.KO_KR, channelType)
        }
        return Optional.empty()
    }
}
