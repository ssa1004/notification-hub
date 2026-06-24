package com.example.notification.adapter.out.memory

import com.example.notification.application.port.out.AdminRateLimiter
import com.example.notification.domain.shared.RateLimitDecision
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * admin endpoint 의 scope × callerKey fixed-window token bucket 의 인메모리 구현 — zero-infra 부팅용.
 *
 * RedisAdminRateLimiter 와 동일한 fixed-window(분당 N회) 동작을 [ConcurrentHashMap] 으로 재현.
 * 단일 인스턴스·비영속. 운영/분산에는 RedisAdminRateLimiter(`notification.redis.enabled=true`).
 */
@Component
@ConditionalOnProperty(name = ["notification.redis.enabled"], havingValue = "false")
class InMemoryAdminRateLimiter : AdminRateLimiter {

    @Value("\${admin.rate-limit.window-ms:60000}") private var windowMs: Long = 60_000
    @Value("\${admin.rate-limit.per-window:60}") private var limit: Long = 60

    private class Window(var endMs: Long, var count: Long)

    private val windows = ConcurrentHashMap<String, Window>()
    private val lock = Any()

    override fun tryConsume(scope: String, callerKey: String): RateLimitDecision =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val w = windows.getOrPut("$scope:$callerKey") { Window(now + windowMs, 0) }
            if (now >= w.endMs) {
                w.endMs = now + windowMs
                w.count = 0
            }
            w.count += 1
            val ttl = w.endMs - now
            if (w.count > limit) {
                RateLimitDecision.deny(if (ttl < 0) windowMs else ttl)
            } else {
                RateLimitDecision.allow(maxOf(0L, limit - w.count))
            }
        }
}
