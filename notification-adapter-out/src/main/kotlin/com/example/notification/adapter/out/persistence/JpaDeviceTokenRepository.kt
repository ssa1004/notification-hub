package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.mapper.DeviceTokenMapper
import com.example.notification.adapter.out.persistence.repository.DeviceTokenJpaRepository
import com.example.notification.application.port.out.DeviceTokenRepository
import com.example.notification.domain.device.DeviceToken
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaDeviceTokenRepository(
    private val jpa: DeviceTokenJpaRepository,
) : DeviceTokenRepository {

    override fun save(token: DeviceToken): DeviceToken =
        DeviceTokenMapper.toDomain(jpa.save(DeviceTokenMapper.toEntity(token)))

    override fun findById(id: UUID): Optional<DeviceToken> =
        jpa.findById(id).map(DeviceTokenMapper::toDomain)

    override fun findActiveByRecipientId(recipientId: RecipientId): List<DeviceToken> =
        jpa.findByRecipientIdAndDisabledAtIsNull(recipientId.value)
            .map(DeviceTokenMapper::toDomain)

    override fun findByToken(token: String): Optional<DeviceToken> =
        jpa.findByToken(token).map(DeviceTokenMapper::toDomain)

    override fun deactivateByToken(token: String) {
        jpa.findByToken(token).ifPresent { e ->
            val d = DeviceTokenMapper.toDomain(e)
            if (d.isActive()) {
                d.disable()
                jpa.save(DeviceTokenMapper.toEntity(d))
            }
        }
    }
}
