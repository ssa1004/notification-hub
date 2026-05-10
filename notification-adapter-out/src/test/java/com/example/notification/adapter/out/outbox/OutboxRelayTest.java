package com.example.notification.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity;
import com.example.notification.adapter.out.persistence.repository.OutboxEventJpaRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * OutboxRelay 의 *부분 진행 보존* 회귀 락. polling 중 interrupt / 일부 실패가 나도 그때까지
 * Kafka 발행에 성공해 PUBLISHED 마킹된 row 는 commit 직전에 반드시 flush 되어야, 다음 poll
 * 에서 같은 메시지가 재발행되지 않는다.
 *
 * <p>DB / Kafka 기동 없이 순수 단위 테스트 — repository 와 KafkaTemplate 만 모킹.
 */
class OutboxRelayTest {

    private OutboxEventJpaRepository jpa;
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private OutboxRelay sut;

    @BeforeEach
    void setUp() {
        jpa = mock(OutboxEventJpaRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kt = mock(KafkaTemplate.class);
        kafkaTemplate = kt;
        sut = new OutboxRelay(jpa, kafkaTemplate);
        setIntField(sut, "batchSize", 50);
        setLongField(sut, "sendTimeoutMs", 3000);
    }

    @Test
    void 비어_있으면_KafkaTemplate_호출_안_함() {
        when(jpa.findPending(any(), any())).thenReturn(List.of());

        sut.run();

        verify(kafkaTemplate, times(0)).send(any(), any(), any());
        verify(jpa, times(0)).saveAll(any());
    }

    @Test
    void 모두_성공이면_PUBLISHED_로_마킹_후_saveAll() {
        List<OutboxEventEntity> rows = List.of(row(1L, "topic-a"), row(2L, "topic-b"));
        when(jpa.findPending(any(), any())).thenReturn(rows);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completed());

        sut.run();

        assertThat(rows).allMatch(r -> "PUBLISHED".equals(r.getStatus()));
        assertThat(rows).allMatch(r -> r.getPublishedAt() != null);
        verify(jpa).saveAll(rows);
    }

    /**
     * 핵심 회귀 락 — batch 중간에 InterruptedException 으로 일찍 return 해도, 이미 PUBLISHED
     * 마킹된 row 는 try/finally 의 saveAll 로 flush 되어야 한다. 이 보장이 깨지면 트랜잭션
     * commit 시 dirty-checking 만 의존하게 되고, 흐름에 따라 다음 polling 에서 같은 메시지가
     * 다시 Kafka 로 발행된다.
     */
    @Test
    void interrupt_되어도_그_전까지_PUBLISHED_된_row_는_flush() throws InterruptedException {
        OutboxEventEntity r1 = row(1L, "topic-ok");
        OutboxEventEntity r2 = row(2L, "topic-interrupt");
        OutboxEventEntity r3 = row(3L, "topic-skipped");
        List<OutboxEventEntity> rows = List.of(r1, r2, r3);
        when(jpa.findPending(any(), any())).thenReturn(rows);

        // 1번은 정상, 2번에서 interrupt → 3번은 시도 안 함 (return).
        // KafkaTemplate.send 는 동기적으로 future 반환. r2 처리에 들어갔을 때 future.get 이 InterruptedException
        // 던지도록 — 미리 currentThread 를 interrupt 해두면 내부 await 가 InterruptedException.
        CompletableFuture<SendResult<String, String>> ok = completed();
        CompletableFuture<SendResult<String, String>> blocking = new CompletableFuture<>(); // 영원히 미완료

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(ok, blocking);

        // r2 처리 들어가기 직전에 interrupt 신호 — future.get 호출 시 즉시 InterruptedException.
        Thread.currentThread().interrupt();

        try {
            sut.run();
        } finally {
            // interrupt 플래그 클린업 (다른 테스트에 영향 없게)
            Thread.interrupted();
        }

        // r1 은 PUBLISHED 로 flush 되어야 한다 (saveAll 호출 시 r1 이 그 안에 포함).
        assertThat(r1.getStatus()).isEqualTo("PUBLISHED");
        assertThat(r1.getPublishedAt()).isNotNull();
        // r2/r3 는 PENDING 유지 (다음 poll 에서 재시도).
        assertThat(r2.getStatus()).isEqualTo("PENDING");
        assertThat(r3.getStatus()).isEqualTo("PENDING");

        // 핵심 — finally 블록의 saveAll 이 호출되어야 한다.
        verify(jpa, atLeastOnce()).saveAll(rows);
    }

    private static OutboxEventEntity row(long id, String topic) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(id);
        e.setTopic(topic);
        e.setKeyValue("k-" + id);
        e.setEventId("evt-" + id);
        e.setEventType("Test");
        e.setPayloadJson("{}");
        e.setStatus("PENDING");
        e.setCreatedAt(Instant.EPOCH);
        return e;
    }

    private static CompletableFuture<SendResult<String, String>> completed() {
        ProducerRecord<String, String> rec = new ProducerRecord<>("t", "k", "v");
        RecordMetadata md = new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(rec, md));
    }

    private static void setIntField(Object target, String name, int value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setInt(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setLongField(Object target, String name, long value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setLong(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
