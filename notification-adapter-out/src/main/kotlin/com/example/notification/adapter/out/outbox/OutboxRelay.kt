package com.example.notification.adapter.out.outbox

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox → Kafka relay. `outbox.relay.enabled=true` 일 때 활성. 짧은 polling 주기로 PENDING
 * row 를 가져와 Kafka 로 발행 후 PUBLISHED 마킹.
 *
 * send() 의 결과를 동기 대기 후 markPublished — 실패 시 다음 polling 에서 재시도. CDC
 * (Debezium) 를 쓰면 polling 자체가 불필요하지만 학습 목적엔 polling 이 충분.
 *
 * 한 번에 가져오는 row 수와 polling 주기는 application.yml 의 `outbox.relay.*` 로 조정.
 */
@Component
class OutboxRelay(
    private val jpa: OutboxEventJpaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${outbox.relay.batch-size:50}")
    private var batchSize: Int = 50

    @Value("\${outbox.relay.send-timeout-ms:3000}")
    private var sendTimeoutMs: Long = 3000

    // 한 poll 트랜잭션의 상한은 @Transactional(timeoutString=...) 으로 직접 property 를
    // 읽는다 (application.yml 의 outbox.relay.tx-timeout-seconds, 기본 30s).

    /**
     * 짧은 주기 polling. `@Transactional(REQUIRES_NEW)` 로 매 호출이 별도 트랜잭션 —
     * 한 row 실패가 다른 row 의 markPublished 를 막지 않게 하려면 row 단위 commit 도 가능하나
     * 여기서는 batch 단위 commit 으로 단순화.
     *
     * row 잠금은 [OutboxEventJpaRepository.findPending] 의 `FOR UPDATE SKIP LOCKED` 가 책임 —
     * 다른 instance 가 같은 row 를 동시에 가져가지 못한다. shutdown / interrupt 로 중단되어도
     * 그때까지 status 변경된 row 는 return 전에 명시적으로 flush 해 부분 진행을 보존한다
     * (안 그러면 dirty-checking 으로 commit 되지만 batch 끝까지 못 가서 N 회 재발행).
     *
     * `timeoutString` — Kafka 지연으로 batch 전체가 오래 걸려도 행 잠금을 무한정 들고 있지
     * 않도록 트랜잭션 상한을 둔다. 넘으면 롤백되어 row 가 PENDING 으로 환원된다.
     */
    @Scheduled(fixedDelayString = "\${outbox.relay.fixed-delay-ms:500}")
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        timeoutString = "\${outbox.relay.tx-timeout-seconds:30}",
    )
    fun run() {
        val pending = jpa.findPending(STATUS_PENDING, PageRequest.of(0, batchSize))
        if (pending.isEmpty()) {
            return
        }
        try {
            for (e in pending) {
                try {
                    kafkaTemplate
                        .send(e.topic, e.keyValue, e.payloadJson)
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS)
                    e.status = STATUS_PUBLISHED
                    e.publishedAt = Instant.now()
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.warn("outbox relay interrupted at id={} (flushing partial progress)", e.id)
                    return
                } catch (ex: ExecutionException) {
                    log.warn(
                        "outbox publish failed id={} topic={} reason={} (will retry next poll)",
                        e.id,
                        e.topic,
                        ex.message,
                    )
                    // status 는 PENDING 유지 → 다음 polling 에서 재시도
                } catch (ex: TimeoutException) {
                    log.warn(
                        "outbox publish failed id={} topic={} reason={} (will retry next poll)",
                        e.id,
                        e.topic,
                        ex.message,
                    )
                }
            }
        } finally {
            // try/finally 로 묶어, 중단 (return / 예외) 시점까지 PUBLISHED 마킹된 row 는 항상
            // commit 직전에 flush. dirty-checking 만 의존하면 RuntimeException 으로 트랜잭션이
            // 롤백되어 발행 성공 row 가 다음 poll 에서 재발행된다.
            jpa.saveAll(pending)
        }
    }

    /**
     * OutboxRelayTest 가 reflection 으로 batchSize / sendTimeoutMs 를 주입하므로 field 노출 유지.
     * 별도 setter 는 정의하지 않음 — `private var` 자체가 ReflectionTestUtils 호환.
     */
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PUBLISHED = "PUBLISHED"
    }
}
