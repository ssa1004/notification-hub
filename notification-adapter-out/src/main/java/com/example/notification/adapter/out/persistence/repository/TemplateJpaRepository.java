package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.TemplateEntity;
import com.example.notification.domain.channel.ChannelType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, UUID> {

    Optional<TemplateEntity> findByTemplateKeyAndLocaleAndChannelType(
            String templateKey, String locale, ChannelType channelType);
}
