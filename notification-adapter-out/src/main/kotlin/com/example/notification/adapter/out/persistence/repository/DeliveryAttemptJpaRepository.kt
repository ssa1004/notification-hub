package com.example.notification.adapter.out.persistence.repository

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryStatus
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeliveryAttemptJpaRepository : JpaRepository<DeliveryAttemptEntity, UUID> {

    fun findByNotificationId(notificationId: UUID): List<DeliveryAttemptEntity>

    /** DLQ cursor 페이지네이션 — id 가 cursor 보다 큰 항목만 N개. */
    fun findByStatusAndIdGreaterThanOrderByIdAsc(
        status: DeliveryStatus,
        cursor: UUID,
        pageable: Pageable,
    ): List<DeliveryAttemptEntity>

    /**
     * DLQ 필터 조회 — EXHAUSTED + optional channelType + createdAt 범위 + failureReason LIKE.
     * cursor 보다 id 큰 항목, id 오름차순.
     *
     * `:errorPattern` 은 null 일 때는 무시 — JPQL 의 `COALESCE(NULL, NULL) = NULL` 평가가
     * DB 별로 달라 사용하지 못함. `(:errorPattern IS NULL OR ...)` 패턴.
     */
    @Query(
        """
        SELECT d FROM DeliveryAttemptEntity d
        WHERE d.status = :status
          AND d.id > :cursor
          AND (:channelType IS NULL OR d.channelType = :channelType)
          AND (:fromInstant IS NULL OR d.createdAt >= :fromInstant)
          AND (:toInstant IS NULL OR d.createdAt <= :toInstant)
          AND (:errorPattern IS NULL OR d.failureReason LIKE :errorPattern)
        ORDER BY d.id ASC
        """,
    )
    fun searchExhausted(
        @Param("status") status: DeliveryStatus,
        @Param("cursor") cursor: UUID,
        @Param("channelType") channelType: ChannelType?,
        @Param("fromInstant") fromInstant: Instant?,
        @Param("toInstant") toInstant: Instant?,
        @Param("errorPattern") errorPattern: String?,
        pageable: Pageable,
    ): List<DeliveryAttemptEntity>

    /** [searchExhausted] 와 동일 조건의 count. dry-run 의 estimate / total 에 사용. */
    @Query(
        """
        SELECT COUNT(d) FROM DeliveryAttemptEntity d
        WHERE d.status = :status
          AND (:channelType IS NULL OR d.channelType = :channelType)
          AND (:fromInstant IS NULL OR d.createdAt >= :fromInstant)
          AND (:toInstant IS NULL OR d.createdAt <= :toInstant)
          AND (:errorPattern IS NULL OR d.failureReason LIKE :errorPattern)
        """,
    )
    fun countExhausted(
        @Param("status") status: DeliveryStatus,
        @Param("channelType") channelType: ChannelType?,
        @Param("fromInstant") fromInstant: Instant?,
        @Param("toInstant") toInstant: Instant?,
        @Param("errorPattern") errorPattern: String?,
    ): Long

    /**
     * stats 집계 — 시간 bucket / channel / errorClass 별 count. errorClass 는 SQL 단에서 만들기
     * 어려워 (DB 함수 의존 → H2/Postgres 비호환) Java 단에서 후처리. 여기서는 raw row 만 가져와
     * [com.example.notification.application.port.out.DeliveryAttemptRepository] 어댑터에서
     * group by 한다 — DB row 수가 한도 안이라는 가정 (운영자가 from/to 로 범위 조절).
     */
    @Query(
        """
        SELECT d FROM DeliveryAttemptEntity d
        WHERE d.status = :status
          AND (:fromInstant IS NULL OR d.createdAt >= :fromInstant)
          AND (:toInstant IS NULL OR d.createdAt <= :toInstant)
        """,
    )
    fun findForStats(
        @Param("status") status: DeliveryStatus,
        @Param("fromInstant") fromInstant: Instant?,
        @Param("toInstant") toInstant: Instant?,
    ): List<DeliveryAttemptEntity>
}
