package com.example.notification.adapter.out.redis

import com.example.notification.application.port.out.IdempotencyStore
import com.example.notification.domain.shared.IdempotencyKey
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis SETNX (SET IF NOT EXISTS) + TTL 로 멱등성 키 점유.
 *
 * Spring Data Redis 의 `setIfAbsent(key, value, ttl)` 가 단일 RTT 로 처리. 분산 환경에서
 * 같은 키가 동시에 들어와도 한 쪽만 true 반환.
 */
@Component
@ConditionalOnProperty(name = ["notification.redis.enabled"], havingValue = "true", matchIfMissing = true)
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
) : IdempotencyStore {

    override fun tryAcquire(key: IdempotencyKey, ttl: Duration): Boolean {
        val result = redis.opsForValue().setIfAbsent(NAMESPACE + key.value, "1", ttl)
        return result == true
    }

    override fun release(key: IdempotencyKey) {
        redis.delete(NAMESPACE + key.value)
    }

    companion object {
        const val NAMESPACE = "notif:idem:"
    }
}
