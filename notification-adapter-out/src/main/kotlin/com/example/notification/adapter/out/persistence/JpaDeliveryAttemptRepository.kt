package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity
import com.example.notification.adapter.out.persistence.mapper.DeliveryAttemptMapper
import com.example.notification.adapter.out.persistence.repository.DeliveryAttemptJpaRepository
import com.example.notification.application.dto.DlqErrorClass
import com.example.notification.application.port.out.DeliveryAttemptRepository
import com.example.notification.application.port.out.DlqStatRow
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryStatus
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class JpaDeliveryAttemptRepository(
    private val jpa: DeliveryAttemptJpaRepository,
) : DeliveryAttemptRepository {

    override fun save(attempt: DeliveryAttempt): DeliveryAttempt =
        DeliveryAttemptMapper.toDomain(jpa.save(DeliveryAttemptMapper.toEntity(attempt)))

    override fun saveAll(attempts: List<DeliveryAttempt>): List<DeliveryAttempt> =
        jpa.saveAll(attempts.map(DeliveryAttemptMapper::toEntity))
            .map(DeliveryAttemptMapper::toDomain)

    override fun findById(id: UUID): Optional<DeliveryAttempt> =
        jpa.findById(id).map(DeliveryAttemptMapper::toDomain)

    override fun findByNotificationId(notificationId: UUID): List<DeliveryAttempt> =
        jpa.findByNotificationId(notificationId).map(DeliveryAttemptMapper::toDomain)

    override fun findByStatusAfter(
        status: DeliveryStatus,
        cursor: UUID?,
        limit: Int,
    ): List<DeliveryAttempt> {
        val cursorEffective = cursor ?: CURSOR_MIN
        return jpa.findByStatusAndIdGreaterThanOrderByIdAsc(
            status, cursorEffective, PageRequest.of(0, limit),
        ).map(DeliveryAttemptMapper::toDomain)
    }

    override fun searchExhausted(
        channelType: ChannelType?,
        from: Instant?,
        to: Instant?,
        errorContains: String?,
        cursor: UUID?,
        limit: Int,
    ): List<DeliveryAttempt> {
        val cursorEffective = cursor ?: CURSOR_MIN
        val pattern = if (errorContains.isNullOrBlank()) null else "%$errorContains%"
        return jpa.searchExhausted(
            DeliveryStatus.EXHAUSTED,
            cursorEffective,
            channelType,
            from,
            to,
            pattern,
            PageRequest.of(0, limit),
        ).map(DeliveryAttemptMapper::toDomain)
    }

    override fun countExhausted(
        channelType: ChannelType?,
        from: Instant?,
        to: Instant?,
        errorContains: String?,
    ): Long {
        val pattern = if (errorContains.isNullOrBlank()) null else "%$errorContains%"
        return jpa.countExhausted(DeliveryStatus.EXHAUSTED, channelType, from, to, pattern)
    }

    override fun aggregateExhaustedStats(
        from: Instant?,
        to: Instant?,
        bucketDuration: Duration,
    ): List<DlqStatRow> {
        val bucketMs = bucketDuration.toMillis()
        require(bucketMs > 0) { "bucketDuration must be positive" }
        // raw row 가져와 Java 단에서 group by — DB 호환 (H2 / Postgres) 위해 SQL date_trunc 미사용.
        // 운영자가 from/to 로 범위를 적절히 조절해 row 수가 적당 (예: 1주일 = 수천건) 한 시나리오 가정.
        val rows: List<DeliveryAttemptEntity> = jpa.findForStats(DeliveryStatus.EXHAUSTED, from, to)
        val bucket = HashMap<StatKey, Long>()
        for (e in rows) {
            val t = e.createdAt.toEpochMilli()
            val bucketStartMs = (t / bucketMs) * bucketMs
            val bucketStart = Instant.ofEpochMilli(bucketStartMs)
            val errorClass = DlqErrorClass.classify(e.failureReason)
            val key = StatKey(bucketStart, e.channelType, errorClass)
            bucket.merge(key, 1L) { a, b -> a + b }
        }
        return bucket.entries.map { en ->
            val k = en.key
            DlqStatRow(k.bucketStart, k.channelType, k.errorClass, en.value)
        }
    }

    private data class StatKey(
        val bucketStart: Instant,
        val channelType: ChannelType,
        val errorClass: String?,
    )

    companion object {
        private val CURSOR_MIN: UUID = UUID(0L, 0L)
    }
}
