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
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:500}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run() {
        List<OutboxEventEntity> pending = jpa.findPending(STATUS_PENDING, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEventEntity e : pending) {
            try {
                kafkaTemplate
                        .send(e.getTopic(), e.getKeyValue(), e.getPayloadJson())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                e.setStatus(STATUS_PUBLISHED);
                e.setPublishedAt(Instant.now());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("outbox relay interrupted at id={}", e.getId());
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
        jpa.saveAll(pending);
    }
}
