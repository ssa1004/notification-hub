package com.example.notification.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Boot context 가 정상 부팅되는지 — 모든 빈의 의존성 주입 / config 읽기까지 검증.
 *
 * <p>Kafka / Redis 가 실제로 떠있지 않아도 부팅이 성공해야 함 (테스트 profile 에서 listener
 * autostart 를 false 로 두는 등 분리). 여기서는 단순화 — Lazy 초기화 의존하고 application.yml
 * 의 H2 + 더미 Redis/Kafka 설정으로 충분.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "outbox.relay.fixed-delay-ms=3600000"
        })
class ApplicationContextTest {

    @Test
    void contextLoads() {}
}
