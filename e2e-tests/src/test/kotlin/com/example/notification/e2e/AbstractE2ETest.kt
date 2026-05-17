package com.example.notification.e2e

import com.example.notification.bootstrap.NotificationApplication
import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 base — Postgres + Redis + Kafka Testcontainers 를 한 번 띄워 모든 e2e 테스트가
 * 공유. JVM 단위로 1set 만 기동되어 비용 절감.
 *
 * Spring Boot 가 @SpringBootTest 로 전체 context 부팅 → REST + Kafka consumer + outbox
 * relay 모두 활성. Mock vendor client 가 즉시 성공 응답.
 */
@SpringBootTest(
    classes = [NotificationApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
abstract class AbstractE2ETest {

    companion object {
        @JvmStatic
        val POSTGRES: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("notification")
                .withUsername("notification")
                .withPassword("notification")

        @JvmStatic
        val REDIS: RedisContainer = RedisContainer(DockerImageName.parse("redis:7-alpine"))

        @JvmStatic
        val KAFKA: KafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))

        init {
            POSTGRES.start()
            REDIS.start()
            KAFKA.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun wire(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url") { POSTGRES.jdbcUrl }
            reg.add("spring.datasource.username") { POSTGRES.username }
            reg.add("spring.datasource.password") { POSTGRES.password }
            reg.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            reg.add("spring.data.redis.host") { REDIS.redisHost }
            reg.add("spring.data.redis.port") { REDIS.redisPort.toString() }
            reg.add("spring.kafka.bootstrap-servers") { KAFKA.bootstrapServers }
        }
    }
}
