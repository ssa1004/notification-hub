package com.example.notification.adapter.out.memory

import com.example.notification.application.port.out.IdempotencyStore
import com.example.notification.domain.shared.IdempotencyKey
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 멱등성 키 점유의 인메모리 구현 — Redis 없이 뜨는 zero-infra 로컬/데모 부팅용.
 *
 * Redis SETNX 대신 [ConcurrentHashMap] + 만료시각으로 같은 키 재요청을 차단한다. 동작은
 * RedisIdempotencyStore 와 동일(키가 없을 때만 점유, 있으면 실패). 단일 인스턴스·비영속
 * (재시작 시 초기화)이라 분산/운영에는 RedisIdempotencyStore 를 쓴다(`notification.redis.enabled=true`).
 */
@Component
@ConditionalOnProperty(name = ["notification.redis.enabled"], havingValue = "false")
class InMemoryIdempotencyStore : IdempotencyStore {

    /** key -> 만료 epochMilli */
    private val keys = ConcurrentHashMap<String, Long>()

    override fun tryAcquire(key: IdempotencyKey, ttl: Duration): Boolean {
        val now = System.currentTimeMillis()
        val expireAt = now + ttl.toMillis()
        while (true) {
            val existing = keys.putIfAbsent(key.value, expireAt) ?: return true // 새로 점유
            if (existing > now) return false // 아직 유효한 점유 — 중복 요청
            // 만료된 점유 — CAS 로 교체(동시성 안전). 실패하면 다른 스레드가 먼저 갱신한 것이라 재시도.
            if (keys.replace(key.value, existing, expireAt)) return true
        }
    }

    override fun release(key: IdempotencyKey) {
        keys.remove(key.value)
    }
}
