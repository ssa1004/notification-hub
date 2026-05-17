package com.example.notification.bootstrap

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.concurrent.Executor

/**
 * 도메인 / 어댑터 전반에서 의존하는 공용 빈 등록.
 *
 * [Clock] — 시스템 시각을 직접 호출하는 자리를 모두 주입형으로 만들어 테스트에서
 * 시각을 고정할 수 있게 한다 (`Clock.fixed(...)`).
 *
 * [TransactionTemplate] — DLQ bulk worker 가 항목당 별도 트랜잭션을 열기 위해 필요.
 * `@Transactional` AOP 는 동일 빈 안에서의 자체 호출에 적용되지 않으므로 Template 로
 * 명시 호출.
 *
 * `dlqBulkExecutor` — DLQ bulk-replay/discard 비동기 worker 전용 thread pool. 작은 pool
 * (1~2 thread) 로 동시 실행 1건만 허용 — vendor 부하 / Outbox 폭주 방지. concurrency 늘리는 건
 * cluster 단에서 다른 pod 가 받게 두는 방향.
 */
@Configuration
internal class CommonBeansConfig {

    @Bean
    open fun clock(): Clock = Clock.systemUTC()

    @Bean
    open fun transactionTemplate(txManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(txManager)

    @Bean("dlqBulkExecutor")
    open fun dlqBulkExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 2
        executor.queueCapacity = 8
        executor.setThreadNamePrefix("dlq-bulk-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }
}
