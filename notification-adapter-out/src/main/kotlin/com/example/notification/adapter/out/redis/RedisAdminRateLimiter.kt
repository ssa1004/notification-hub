package com.example.notification.adapter.out.redis

import com.example.notification.application.port.out.AdminRateLimiter
import com.example.notification.domain.shared.RateLimitDecision
import java.util.Collections
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * admin endpoint 의 IP × scope 별 token bucket. 일반 [RedisRateLimiter] 와 같은 INCR + PEXPIRE Lua
 * 패턴이지만 키 구조와 한도가 다름.
 *
 * 키 구조: `notif:admin:rl:<scope>:<callerKey>`. scope = list / replay / discard / bulk 등.
 * callerKey = 호출자 IP.
 *
 * 한도는 분당 60 (window 60s) — 사람이 손으로 누르는 빈도 가정. bulk 같은 무거운 작업은
 * 별도 scope 로 더 낮게 잡을 수 있도록 분리.
 */
@Component
class RedisAdminRateLimiter(
    private val redis: StringRedisTemplate,
) : AdminRateLimiter {

    @Value("\${admin.rate-limit.window-ms:60000}")
    private var windowMs: Long = 60_000

    @Value("\${admin.rate-limit.per-window:60}")
    private var limit: Long = 60

    override fun tryConsume(scope: String, callerKey: String): RateLimitDecision {
        val key = NAMESPACE + scope + ":" + callerKey
        @Suppress("UNCHECKED_CAST")
        val result = redis.execute(
            SCRIPT,
            Collections.singletonList(key),
            windowMs.toString(),
        ) as List<Long>
        val current = result[0]
        val ttl = result[1]
        if (current > limit) {
            return RateLimitDecision.deny(if (ttl < 0) windowMs else ttl)
        }
        return RateLimitDecision.allow(maxOf(0L, limit - current))
    }

    companion object {
        const val NAMESPACE = "notif:admin:rl:"

        private const val LUA_INCR_AND_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
        """

        @Suppress("UNCHECKED_CAST")
        private val SCRIPT: DefaultRedisScript<List<*>> =
            DefaultRedisScript(LUA_INCR_AND_TTL, List::class.java)
    }
}
