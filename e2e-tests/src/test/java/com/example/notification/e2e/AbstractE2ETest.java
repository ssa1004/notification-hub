package com.example.notification.e2e;

import com.example.notification.bootstrap.NotificationApplication;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 base — Postgres + Redis + Kafka Testcontainers 를 한 번 띄워 모든 e2e 테스트가
 * 공유. JVM 단위로 1set 만 기동되어 비용 절감.
 *
 * <p>Spring Boot 가 @SpringBootTest 로 전체 context 부팅 → REST + Kafka consumer + outbox
 * relay 모두 활성. Mock vendor client 가 즉시 성공 응답.
 */
@SpringBootTest(
        classes = NotificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractE2ETest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("notification")
                    .withUsername("notification")
                    .withPassword("notification");

    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void wire(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        reg.add("spring.datasource.username", POSTGRES::getUsername);
        reg.add("spring.datasource.password", POSTGRES::getPassword);
        reg.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        reg.add("spring.data.redis.host", REDIS::getRedisHost);
        reg.add("spring.data.redis.port", () -> String.valueOf(REDIS.getRedisPort()));
        reg.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
