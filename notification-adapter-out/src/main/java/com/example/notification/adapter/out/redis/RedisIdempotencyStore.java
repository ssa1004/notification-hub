package com.example.notification.adapter.out.redis;

import com.example.notification.application.port.out.IdempotencyStore;
import com.example.notification.domain.shared.IdempotencyKey;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis SETNX (SET IF NOT EXISTS) + TTL 로 멱등성 키 점유.
 *
 * <p>Spring Data Redis 의 {@code setIfAbsent(key, value, ttl)} 가 단일 RTT 로 처리. 분산 환경에서
 * 같은 키가 동시에 들어와도 한 쪽만 true 반환.
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    static final String NAMESPACE = "notif:idem:";

    private final StringRedisTemplate redis;

    @Override
    public boolean tryAcquire(IdempotencyKey key, Duration ttl) {
        Boolean result = redis.opsForValue().setIfAbsent(NAMESPACE + key.value(), "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void release(IdempotencyKey key) {
        redis.delete(NAMESPACE + key.value());
    }
}
