package com.example.notification.adapter.out.persistence.repository

import com.example.notification.adapter.out.persistence.entity.DeviceTokenEntity
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenEntity, UUID> {

    fun findByToken(token: String): Optional<DeviceTokenEntity>

    fun findByRecipientIdAndDisabledAtIsNull(recipientId: String): List<DeviceTokenEntity>
}
