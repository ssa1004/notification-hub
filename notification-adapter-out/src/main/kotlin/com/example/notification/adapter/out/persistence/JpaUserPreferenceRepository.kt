package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.mapper.UserPreferenceMapper
import com.example.notification.adapter.out.persistence.repository.UserPreferenceJpaRepository
import com.example.notification.application.port.out.UserPreferenceRepository
import com.example.notification.domain.preference.UserPreference
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import org.springframework.stereotype.Repository

@Repository
class JpaUserPreferenceRepository(
    private val jpa: UserPreferenceJpaRepository,
) : UserPreferenceRepository {

    override fun findByRecipientId(recipientId: RecipientId): Optional<UserPreference> =
        jpa.findById(recipientId.value).map(UserPreferenceMapper::toDomain)

    override fun save(preference: UserPreference): UserPreference =
        UserPreferenceMapper.toDomain(jpa.save(UserPreferenceMapper.toEntity(preference)))
}
