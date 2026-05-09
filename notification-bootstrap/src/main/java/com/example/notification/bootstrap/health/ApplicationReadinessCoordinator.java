package com.example.notification.bootstrap.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 hub 의 readiness/liveness 신호 조정자.
 *
 * <p>Spring Boot 가 제공하는 {@link ApplicationAvailability} 를 직접 publish 하여 K8s probe
 * 가 읽는 {@code /actuator/health/{readiness,liveness}} 가 외부 의존 (Kafka producer / Redis)
 * 의 실시간 상태를 반영하도록 함.
 *
 * <ul>
 *   <li>Liveness — process 가 살아있는지만. JVM 이 deadlock 이거나 OOM 직전이면 K8s 가 재기동
 *       해야 함. Kafka/Redis 일시 단절은 liveness 와 무관 — 외부 의존 끊어졌다고 process 죽이면
 *       cascade 장애 (의존 복구 후에도 pod 끝없이 재기동) 를 만든다.
 *   <li>Readiness — 트래픽 받기 충분한가. Kafka producer / Redis 둘 중 하나라도 단절이면
 *       {@link ReadinessState#REFUSING_TRAFFIC} 으로 전환 → service endpoint 에서 빠짐 →
 *       LB 가 새 요청을 다른 pod 로. 의존 복구되면 자동으로 ACCEPTING_TRAFFIC 복귀.
 * </ul>
 *
 * <p>외부 의존 ping 은 5s 주기 — probe 주기 (5s/10s) 와 비슷한 시간 안에 상태 반영. 실제 호출
 * 은 가벼운 connection check 만 (Redis ping, Kafka admin describeCluster).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationReadinessCoordinator {

    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationAvailability availability;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaAdmin kafkaAdmin;

    @Scheduled(fixedDelayString = "${readiness.probe-interval-ms:5000}")
    public void recheck() {
        boolean redisOk = checkRedis();
        boolean kafkaOk = checkKafka();
        boolean shouldAcceptTraffic = redisOk && kafkaOk;

        ReadinessState current = availability.getReadinessState();
        ReadinessState target =
                shouldAcceptTraffic
                        ? ReadinessState.ACCEPTING_TRAFFIC
                        : ReadinessState.REFUSING_TRAFFIC;

        if (current != target) {
            log.warn(
                    "readiness 변경: {} → {} (redis={} kafka={})",
                    current,
                    target,
                    redisOk,
                    kafkaOk);
            AvailabilityChangeEvent.publish(eventPublisher, this, target);
        }

        // liveness 는 process 자체가 살아있다는 신호만 — 외부 의존과 무관.
        // Spring Boot 가 기본적으로 CORRECT 로 시작하지만, 명시적으로 유지해 의도 표현.
        if (availability.getLivenessState() != LivenessState.CORRECT) {
            AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.CORRECT);
        }
    }

    private boolean checkRedis() {
        try (var conn = redisConnectionFactory.getConnection()) {
            String pong = conn.ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.debug("Redis ping 실패: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkKafka() {
        try {
            // clusterId() 는 admin client 로 metadata RPC 한 번 — 가벼움. broker 단절이면 null.
            return kafkaAdmin.clusterId() != null;
        } catch (Exception e) {
            log.debug("Kafka clusterId 조회 실패: {}", e.getMessage());
            return false;
        }
    }
}
