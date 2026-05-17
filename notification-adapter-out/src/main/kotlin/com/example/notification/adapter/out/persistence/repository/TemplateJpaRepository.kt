package com.example.notification.adapter.out.persistence.repository

import com.example.notification.adapter.out.persistence.entity.TemplateEntity
import com.example.notification.domain.channel.ChannelType
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TemplateJpaRepository : JpaRepository<TemplateEntity, UUID> {

    fun findByTemplateKeyAndLocaleAndChannelType(
        templateKey: String,
        locale: String,
        channelType: ChannelType,
    ): Optional<TemplateEntity>
}
