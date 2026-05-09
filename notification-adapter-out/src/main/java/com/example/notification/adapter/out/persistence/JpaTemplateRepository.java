package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.mapper.TemplateMapper;
import com.example.notification.adapter.out.persistence.repository.TemplateJpaRepository;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.Locale;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaTemplateRepository implements TemplateRepository {

    private final TemplateJpaRepository jpa;

    @Override
    public Template save(Template template) {
        return TemplateMapper.toDomain(jpa.save(TemplateMapper.toEntity(template)));
    }

    @Override
    public Optional<Template> find(TemplateKey key, Locale locale, ChannelType channelType) {
        return jpa.findByTemplateKeyAndLocaleAndChannelType(
                        key.value(), locale.tag(), channelType)
                .map(TemplateMapper::toDomain);
    }

    @Override
    public Optional<Template> findWithFallback(
            TemplateKey key, Locale preferred, ChannelType channelType) {
        Optional<Template> direct = find(key, preferred, channelType);
        if (direct.isPresent()) return direct;
        if (!preferred.equals(Locale.KO_KR)) {
            return find(key, Locale.KO_KR, channelType);
        }
        return Optional.empty();
    }
}
