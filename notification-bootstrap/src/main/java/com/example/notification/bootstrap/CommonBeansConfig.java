package com.example.notification.bootstrap;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 / 어댑터 전반에서 의존하는 공용 빈 등록.
 *
 * <p>{@link Clock} — 시스템 시각을 직접 호출하는 자리를 모두 주입형으로 만들어 테스트에서
 * 시각을 고정할 수 있게 한다 ({@code Clock.fixed(...)}).
 */
@Configuration
class CommonBeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
