package com.example.notification.bootstrap;

import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도메인 / 어댑터 전반에서 의존하는 공용 빈 등록.
 *
 * <p>{@link Clock} — 시스템 시각을 직접 호출하는 자리를 모두 주입형으로 만들어 테스트에서
 * 시각을 고정할 수 있게 한다 ({@code Clock.fixed(...)}).
 *
 * <p>{@link TransactionTemplate} — DLQ bulk worker 가 항목당 별도 트랜잭션을 열기 위해 필요.
 * {@code @Transactional} AOP 는 동일 빈 안에서의 자체 호출에 적용되지 않으므로 TemplaTe 로
 * 명시 호출.
 *
 * <p>{@code dlqBulkExecutor} — DLQ bulk-replay/discard 비동기 worker 전용 thread pool. 작은 pool
 * (1~2 thread) 로 동시 실행 1건만 허용 — vendor 부하 / Outbox 폭주 방지. concurrency 늘리는 건
 * cluster 단에서 다른 pod 가 받게 두는 방향.
 */
@Configuration
class CommonBeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    @Bean("dlqBulkExecutor")
    Executor dlqBulkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("dlq-bulk-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
