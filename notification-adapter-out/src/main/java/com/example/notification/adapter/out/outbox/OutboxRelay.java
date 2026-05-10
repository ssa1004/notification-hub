package com.example.notification.adapter.out.outbox;

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity;
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox → Kafka relay. {@code outbox.relay.enabled=true} 일 때 활성. 짧은 polling 주기로 PENDING
 * row 를 가져와 Kafka 로 발행 후 PUBLISHED 마킹.
 *
 * <p>send() 의 결과를 *동기 대기* 후 markPublished — 실패 시 다음 polling 에서 재시도. CDC
 * (Debezium) 를 쓰면 polling 자체가 불필요하지만 학습 목적엔 polling 이 충분.
 *
 * <p>한 번에 가져오는 row 수와 polling 주기는 application.yml 의 {@code outbox.relay.*} 로 조정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_PUBLISHED = "PUBLISHED";

    private final OutboxEventJpaRepository jpa;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${outbox.relay.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    /**
     * 짧은 주기 polling. {@code @Transactional(REQUIRES_NEW)} 로 매 호출이 별도 트랜잭션 —
     * 한 row 실패가 다른 row 의 markPublished 를 막지 않게 하려면 row 단위 commit 도 가능하나
     * 여기서는 batch 단위 commit 으로 단순화.
     *
     * <p>row 잠금은 {@link OutboxEventJpaRepository#findPending} 의 {@code FOR UPDATE SKIP LOCKED}
     * 가 책임 — 다른 instance 가 같은 row 를 동시에 가져가지 못한다. shutdown / interrupt 로
     * 중단되어도 그때까지 status 변경된 row 는 *return 전에* 명시적으로 flush 해 부분 진행을
     * 보존한다 (안 그러면 dirty-checking 으로 commit 되지만 batch 끝까지 못 가서 N 회 재발행).
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:500}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run() {
        List<OutboxEventEntity> pending = jpa.findPending(STATUS_PENDING, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }
        try {
            for (OutboxEventEntity e : pending) {
                try {
                    kafkaTemplate
                            .send(e.getTopic(), e.getKeyValue(), e.getPayloadJson())
                            .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                    e.setStatus(STATUS_PUBLISHED);
                    e.setPublishedAt(Instant.now());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("outbox relay interrupted at id={} (flushing partial progress)", e.getId());
                    return;
                } catch (ExecutionException | TimeoutException ex) {
                    log.warn(
                            "outbox publish failed id={} topic={} reason={} (will retry next poll)",
                            e.getId(),
                            e.getTopic(),
                            ex.getMessage());
                    // status 는 PENDING 유지 → 다음 polling 에서 재시도
                }
            }
        } finally {
            // try/finally 로 묶어, 중단 (return / 예외) 시점까지 PUBLISHED 마킹된 row 는 항상
            // commit 직전에 flush. dirty-checking 만 의존하면 RuntimeException 으로 트랜잭션이
            // 롤백되어 발행 성공 row 가 다음 poll 에서 재발행된다.
            jpa.saveAll(pending);
        }
    }
}
