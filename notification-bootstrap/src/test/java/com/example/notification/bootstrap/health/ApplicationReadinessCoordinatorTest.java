package com.example.notification.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;

class ApplicationReadinessCoordinatorTest {

    private RecordingPublisher publisher;
    private FakeAvailability availability;
    private RedisConnectionFactory redisFactory;
    private RedisConnection redisConn;
    private KafkaAdmin kafkaAdmin;
    private ApplicationReadinessCoordinator sut;

    @BeforeEach
    void setUp() {
        publisher = new RecordingPublisher();
        availability = new FakeAvailability();
        redisConn = mock(RedisConnection.class);
        redisFactory = mock(RedisConnectionFactory.class);
        when(redisFactory.getConnection()).thenReturn(redisConn);
        kafkaAdmin = mock(KafkaAdmin.class);
        sut =
                new ApplicationReadinessCoordinator(
                        publisher, availability, redisFactory, kafkaAdmin);
    }

    @Test
    void Redis_와_Kafka_정상이면_ACCEPTING_TRAFFIC_으로_전환() {
        when(redisConn.ping()).thenReturn("PONG");
        when(kafkaAdmin.clusterId()).thenReturn("cluster-1");
        availability.readiness = ReadinessState.REFUSING_TRAFFIC;

        sut.recheck();

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void Redis_단절이면_REFUSING_TRAFFIC() {
        when(redisConn.ping()).thenThrow(new RuntimeException("connection refused"));
        when(kafkaAdmin.clusterId()).thenReturn("cluster-1");
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC;

        sut.recheck();

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    void Kafka_단절이면_REFUSING_TRAFFIC() {
        when(redisConn.ping()).thenReturn("PONG");
        when(kafkaAdmin.clusterId()).thenReturn(null);
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC;

        sut.recheck();

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    void 외부_의존_단절이어도_liveness_는_CORRECT_유지() {
        when(redisConn.ping()).thenThrow(new RuntimeException("redis down"));
        when(kafkaAdmin.clusterId()).thenThrow(new RuntimeException("kafka down"));
        availability.liveness = LivenessState.BROKEN;

        sut.recheck();

        // liveness 는 외부 의존과 무관 — process alive 만 — CORRECT 로 복구되어야.
        assertThat(publisher.lastLiveness()).isEqualTo(LivenessState.CORRECT);
    }

    @Test
    void 같은_상태가_지속되면_이벤트_재발행_안_함() {
        when(redisConn.ping()).thenReturn("PONG");
        when(kafkaAdmin.clusterId()).thenReturn("cluster-1");
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC;
        availability.liveness = LivenessState.CORRECT;

        sut.recheck();

        // 상태 동일 → readiness 이벤트 발행 안 됨 (liveness 도 마찬가지).
        assertThat(publisher.events).isEmpty();
    }

    private static class FakeAvailability implements ApplicationAvailability {
        ReadinessState readiness = ReadinessState.REFUSING_TRAFFIC;
        LivenessState liveness = LivenessState.CORRECT;

        @Override
        public ReadinessState getReadinessState() {
            return readiness;
        }

        @Override
        public LivenessState getLivenessState() {
            return liveness;
        }

        @Override
        public <S extends org.springframework.boot.availability.AvailabilityState> S getState(
                Class<S> stateType) {
            return getState(stateType, null);
        }

        @Override
        public <S extends org.springframework.boot.availability.AvailabilityState> S getState(
                Class<S> stateType, S defaultState) {
            if (stateType == ReadinessState.class) {
                return stateType.cast(readiness);
            }
            if (stateType == LivenessState.class) {
                return stateType.cast(liveness);
            }
            return defaultState;
        }

        @Override
        public <S extends org.springframework.boot.availability.AvailabilityState>
                AvailabilityChangeEvent<S> getLastChangeEvent(Class<S> stateType) {
            return null;
        }
    }

    private static class RecordingPublisher implements ApplicationEventPublisher {
        java.util.List<ApplicationEvent> events = new java.util.ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof ApplicationEvent ae) {
                events.add(ae);
            }
        }

        ReadinessState lastReadiness() {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i) instanceof AvailabilityChangeEvent<?> e
                        && e.getState() instanceof ReadinessState rs) {
                    return rs;
                }
            }
            return null;
        }

        LivenessState lastLiveness() {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i) instanceof AvailabilityChangeEvent<?> e
                        && e.getState() instanceof LivenessState ls) {
                    return ls;
                }
            }
            return null;
        }
    }
}
