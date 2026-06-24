package com.example.notification.adapter.out.redis

import com.example.notification.application.port.out.RateLimiter
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.RateLimitDecision
import java.time.Duration
import java.util.Collections
import java.util.EnumMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * recipient × channel 별 token bucket. Redis 의 INCR + EXPIRE 조합 (Lua 스크립트로 원자) 으로
 * 분당 N개 처리.
 *
 * 운영에선 leaky bucket / sliding window 도 있지만 이 hub 는 단순 fixed-window 를 사용.
 * 1 초 단위 윈도우의 startover 직후 burst 가 가능한 단점 인지하고 사용 (ADR-0006 참고).
 *
 * 한도는 채널별 차등 — push/email 은 분당 30, sms/알림톡 은 분당 5 (vendor 비용 고려).
 */
@Component
@ConditionalOnProperty(name = ["notification.redis.enabled"], havingValue = "true", matchIfMissing = true)
class RedisRateLimiter(
    private val redis: StringRedisTemplate,
) : RateLimiter {

    @Value("\${ratelimit.window-ms:60000}")
    private var windowMs: Long = 60_000

    @Value("\${ratelimit.push-per-window:30}")
    private var pushLimit: Long = 30

    @Value("\${ratelimit.email-per-window:30}")
    private var emailLimit: Long = 30

    @Value("\${ratelimit.sms-per-window:5}")
    private var smsLimit: Long = 5

    @Value("\${ratelimit.kakao-per-window:5}")
    private var kakaoLimit: Long = 5

    override fun tryConsume(recipientId: RecipientId, channel: ChannelType): RateLimitDecision {
        val limit = limitFor(channel)
        val key = NAMESPACE + channel.name + ":" + recipientId.value
        @Suppress("UNCHECKED_CAST")
        val result = redis.execute(
            SCRIPT,
            Collections.singletonList(key),
            windowMs.toString(),
        ) as List<Long>
        val current = result[0]
        val ttl = result[1]
        if (current > limit) {
            return RateLimitDecision.deny(ttl)
        }
        return RateLimitDecision.allow(maxOf(0L, limit - current))
    }

    override fun tryConsumeAll(
        recipientId: RecipientId,
        demand: Map<ChannelType, Int>,
    ): Map<ChannelType, RateLimitDecision> {
        if (demand.isEmpty()) {
            return emptyMap()
        }
        // 입력 검증: demand 값은 양수여야 함. 0/음수면 호출자가 잘못 만든 것.
        for ((k, v) in demand) {
            require(v > 0) { "demand must be positive for $k: got $v" }
        }

        // 결정성을 위해 ChannelType 순서 (enum 정의 순) 로 정렬해 KEYS / ARGV 작성.
        val ordered = ChannelType.values().filter { demand.containsKey(it) }
        val keys = ordered.map { NAMESPACE + it.name + ":" + recipientId.value }
        val args = ArrayList<String>(1 + ordered.size * 2)
        args.add(windowMs.toString())
        // limits 먼저, demands 다음 (Lua 스크립트의 인덱싱 규약).
        for (t in ordered) args.add(limitFor(t).toString())
        for (t in ordered) args.add(demand[t]!!.toString())

        @Suppress("UNCHECKED_CAST")
        val raw = redis.execute(BATCH_SCRIPT, keys, *args.toArray()) as List<Long>
        val state = raw[0]
        val result = EnumMap<ChannelType, RateLimitDecision>(ChannelType::class.java)
        for (i in ordered.indices) {
            val t = ordered[i]
            val current = raw[1 + i * 2]
            val ttl = raw[2 + i * 2]
            val limit = limitFor(t)
            if (state == 0L) {
                // 모두 거절 — 차단된 채널은 retryAfter 로 ttl 노출. 통과 가능했던 채널도 의미 보존
                // 위해 deny 로 표기 (호출자는 어차피 묶음 재시도해야 함).
                val retryAfter = if (ttl >= 0) ttl else windowMs
                // 이 채널은 자체로는 통과 가능했지만 다른 채널 거절로 묶음 차감 보류.
                // 호출자가 묶음 단위로 재시도하므로 deny 로 통일.
                result[t] = RateLimitDecision.deny(retryAfter)
            } else {
                result[t] = RateLimitDecision.allow(maxOf(0L, limit - current))
            }
        }
        return result
    }

    private fun limitFor(channel: ChannelType): Long = when (channel) {
        ChannelType.PUSH -> pushLimit
        ChannelType.EMAIL -> emailLimit
        ChannelType.SMS -> smsLimit
        ChannelType.KAKAO_ALIMTALK -> kakaoLimit
    }

    /** 테스트용 — 윈도우 강제 reset. */
    internal fun reset(recipientId: RecipientId, channel: ChannelType) {
        redis.delete(NAMESPACE + channel.name + ":" + recipientId.value)
    }

    /** Default 한도 (테스트에서 직접 사용). */
    fun window(): Duration = Duration.ofMillis(windowMs)

    companion object {
        const val NAMESPACE = "notif:rl:"

        /** Lua 스크립트로 INCR + 첫 hit 일 때만 EXPIRE 설정. 두 명령 사이 race 방지. */
        private const val LUA_INCR_AND_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
        """

        /**
         * 여러 키 원자 차감. ARGV[1] = windowMs, ARGV[2..N+1] = limit_i, ARGV[N+2..2N+1] = demand_i.
         * 1) 모든 키의 current 를 읽어 (current+demand) > limit 가 하나라도 있으면 INCRBY 안 함.
         * 2) 가능하면 일괄 INCRBY + 첫 hit 키에만 PEXPIRE.
         * 반환: {state, current_1, ttl_1, current_2, ttl_2, ...} — state=1 이면 모두 통과,
         * state=0 이면 모두 거절 (이때 current_i 는 INCRBY 전 값, 즉 그대로 유지된 값).
         */
        private const val LUA_BATCH_TRY_CONSUME = """
            local n = #KEYS
            local windowMs = ARGV[1]
            local denied = false
            local currents = {}
            for i = 1, n do
              local cur = tonumber(redis.call('GET', KEYS[i])) or 0
              local limit = tonumber(ARGV[1 + i])
              local demand = tonumber(ARGV[1 + n + i])
              currents[i] = cur
              if cur + demand > limit then
                denied = true
              end
            end
            local result = {}
            if denied then
              result[1] = 0
              for i = 1, n do
                local ttl = tonumber(redis.call('PTTL', KEYS[i]))
                if ttl == nil or ttl < 0 then ttl = -1 end
                result[#result+1] = currents[i]
                result[#result+1] = ttl
              end
              return result
            end
            result[1] = 1
            for i = 1, n do
              local demand = tonumber(ARGV[1 + n + i])
              local newval = redis.call('INCRBY', KEYS[i], demand)
              if currents[i] == 0 then
                redis.call('PEXPIRE', KEYS[i], windowMs)
              end
              local ttl = tonumber(redis.call('PTTL', KEYS[i]))
              if ttl == nil or ttl < 0 then ttl = -1 end
              result[#result+1] = newval
              result[#result+1] = ttl
            end
            return result
        """

        // Spring 의 DefaultRedisScript 는 result type 으로 raw List 를 기대 — element 타입이 Lua
        // 반환 구조에 따라 달라지므로 generic 으로 좁히지 못한다. raw 사용은 명시적으로 suppress.
        @Suppress("UNCHECKED_CAST")
        private val SCRIPT: DefaultRedisScript<List<*>> =
            DefaultRedisScript(LUA_INCR_AND_TTL, List::class.java)

        @Suppress("UNCHECKED_CAST")
        private val BATCH_SCRIPT: DefaultRedisScript<List<*>> =
            DefaultRedisScript(LUA_BATCH_TRY_CONSUME, List::class.java)
    }
}
