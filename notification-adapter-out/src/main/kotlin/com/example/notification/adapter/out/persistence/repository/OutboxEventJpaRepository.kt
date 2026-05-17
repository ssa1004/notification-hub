package com.example.notification.adapter.out.persistence.repository

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, Long> {

    /**
     * polling 용 PENDING row 조회. `FOR UPDATE SKIP LOCKED` 로 현재 트랜잭션이 잠그지 못한
     * row 는 건너뜀 — 다중 인스턴스가 동시에 polling 해도 같은 row 가 두 instance 에 동시에 잡혀
     * Kafka 로 중복 publish 되는 amplification 을 막는다 (3 replica 면 약 3배 중복 발행 위험).
     *
     * 네이티브 쿼리로 작성한 이유: JPA 표준은 `jakarta.persistence.lock.timeout = -2` 힌트
     * 매핑이 driver/벤더별로 흔들리므로, PostgreSQL 과 H2(PostgreSQL mode) 가 동일 문법을
     * 안정적으로 해석하는 native SQL 이 회귀에 더 강함.
     *
     * at-least-once 자체는 ADR-0004 의 정책 (consumer 측 eventId dedup) — 본 락은 정상 경로
     * 에서 부주의한 중복 발행만 차단.
     */
    @Query(
        value =
            "SELECT * FROM outbox_event WHERE status = :status " +
                "ORDER BY id ASC " +
                "LIMIT :#{#pageable.pageSize} " +
                "FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun findPending(@Param("status") status: String, pageable: Pageable): List<OutboxEventEntity>
}
