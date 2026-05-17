package com.example.notification.bootstrap.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.AvailabilityState
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.kafka.core.KafkaAdmin

internal class ApplicationReadinessCoordinatorTest {

    private lateinit var publisher: RecordingPublisher
    private lateinit var availability: FakeAvailability
    private lateinit var redisFactory: RedisConnectionFactory
    private lateinit var redisConn: RedisConnection
    private lateinit var kafkaAdmin: KafkaAdmin
    private lateinit var sut: ApplicationReadinessCoordinator

    @BeforeEach
    fun setUp() {
        publisher = RecordingPublisher()
        availability = FakeAvailability()
        redisConn = mock()
        redisFactory = mock()
        whenever(redisFactory.connection).thenReturn(redisConn)
        kafkaAdmin = mock()
        sut = ApplicationReadinessCoordinator(publisher, availability, redisFactory, kafkaAdmin)
    }

    @Test
    fun Redis_와_Kafka_정상이면_ACCEPTING_TRAFFIC_으로_전환() {
        whenever(redisConn.ping()).thenReturn("PONG")
        whenever(kafkaAdmin.clusterId()).thenReturn("cluster-1")
        availability.readiness = ReadinessState.REFUSING_TRAFFIC

        sut.recheck()

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC)
    }

    @Test
    fun Redis_단절이면_REFUSING_TRAFFIC() {
        whenever(redisConn.ping()).thenThrow(RuntimeException("connection refused"))
        whenever(kafkaAdmin.clusterId()).thenReturn("cluster-1")
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC

        sut.recheck()

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.REFUSING_TRAFFIC)
    }

    @Test
    fun Kafka_단절이면_REFUSING_TRAFFIC() {
        whenever(redisConn.ping()).thenReturn("PONG")
        whenever(kafkaAdmin.clusterId()).thenReturn(null)
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC

        sut.recheck()

        assertThat(publisher.lastReadiness()).isEqualTo(ReadinessState.REFUSING_TRAFFIC)
    }

    @Test
    fun 외부_의존_단절이어도_liveness_는_CORRECT_유지() {
        whenever(redisConn.ping()).thenThrow(RuntimeException("redis down"))
        whenever(kafkaAdmin.clusterId()).thenThrow(RuntimeException("kafka down"))
        availability.liveness = LivenessState.BROKEN

        sut.recheck()

        // liveness 는 외부 의존과 무관 — process alive 만 — CORRECT 로 복구되어야.
        assertThat(publisher.lastLiveness()).isEqualTo(LivenessState.CORRECT)
    }

    @Test
    fun 같은_상태가_지속되면_이벤트_재발행_안_함() {
        whenever(redisConn.ping()).thenReturn("PONG")
        whenever(kafkaAdmin.clusterId()).thenReturn("cluster-1")
        availability.readiness = ReadinessState.ACCEPTING_TRAFFIC
        availability.liveness = LivenessState.CORRECT

        sut.recheck()

        // 상태 동일 → readiness 이벤트 발행 안 됨 (liveness 도 마찬가지).
        assertThat(publisher.events).isEmpty()
    }

    private class FakeAvailability : ApplicationAvailability {
        var readiness: ReadinessState = ReadinessState.REFUSING_TRAFFIC
        var liveness: LivenessState = LivenessState.CORRECT

        override fun getReadinessState(): ReadinessState = readiness

        override fun getLivenessState(): LivenessState = liveness

        override fun <S : AvailabilityState> getState(stateType: Class<S>): S =
            getState(stateType, null)

        @Suppress("UNCHECKED_CAST")
        override fun <S : AvailabilityState> getState(stateType: Class<S>, defaultState: S?): S {
            if (stateType == ReadinessState::class.java) {
                return readiness as S
            }
            if (stateType == LivenessState::class.java) {
                return liveness as S
            }
            return defaultState as S
        }

        override fun <S : AvailabilityState> getLastChangeEvent(
            stateType: Class<S>,
        ): AvailabilityChangeEvent<S>? = null
    }

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<ApplicationEvent> = mutableListOf()

        override fun publishEvent(event: Any) {
            if (event is ApplicationEvent) {
                events.add(event)
            }
        }

        fun lastReadiness(): ReadinessState? {
            for (i in events.indices.reversed()) {
                val e = events[i]
                if (e is AvailabilityChangeEvent<*>) {
                    val state = e.state
                    if (state is ReadinessState) {
                        return state
                    }
                }
            }
            return null
        }

        fun lastLiveness(): LivenessState? {
            for (i in events.indices.reversed()) {
                val e = events[i]
                if (e is AvailabilityChangeEvent<*>) {
                    val state = e.state
                    if (state is LivenessState) {
                        return state
                    }
                }
            }
            return null
        }
    }
}
