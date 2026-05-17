package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.mapper.RecipientMapper
import com.example.notification.adapter.out.persistence.repository.RecipientJpaRepository
import com.example.notification.application.port.out.RecipientRepository
import com.example.notification.domain.recipient.Recipient
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import org.springframework.stereotype.Repository

@Repository
class JpaRecipientRepository(
    private val jpa: RecipientJpaRepository,
) : RecipientRepository {

    override fun findById(id: RecipientId): Optional<Recipient> =
        jpa.findById(id.value).map(RecipientMapper::toDomain)

    /** 테스트/seed 용. 실제론 별도 user/auth service 가 master. */
    fun save(r: Recipient): Recipient =
        RecipientMapper.toDomain(jpa.save(RecipientMapper.toEntity(r)))
}
